# Healthcheck for host-published service ports (from .env).
# Production (P0.5, no host ports except Caddy): .\scripts\healthcheck.ps1 -Prod
param(
  [switch]$Prod
)

$ErrorActionPreference = "Continue"

$Root = Split-Path -Parent (Split-Path -Parent $MyInvocation.MyCommand.Path)
Set-Location $Root

function Get-EnvValue([string]$Key, [string]$Default) {
  if (Test-Path ".env") {
    $line = Get-Content ".env" | Where-Object { $_ -match "^$Key=" } | Select-Object -Last 1
    if ($line -and $line -match "^$Key=(.+)$") {
      return $Matches[1].Trim()
    }
  }
  return $Default
}

$fail = 0

function Test-Endpoint([string]$Name, [string]$Url, [string]$Expect = "") {
  try {
    $resp = Invoke-WebRequest -Uri $Url -UseBasicParsing -TimeoutSec 10
    $body = $resp.Content
    if ($Expect -and ($body -notmatch [regex]::Escape($Expect)) -and ($body -notlike "*$Expect*")) {
      Write-Host "FAIL  $Name ($Url) - unexpected response"
      $script:fail = 1
    }
    else {
      Write-Host "OK    $Name"
    }
  }
  catch {
    Write-Host "FAIL  $Name ($Url)"
    $script:fail = 1
  }
}

function Test-ContainerHealth([string]$Name, [string]$Container) {
  $status = docker inspect --format "{{if .State.Health}}{{.State.Health.Status}}{{else}}{{.State.Status}}{{end}}" $Container 2>$null
  if ($status -eq "healthy") {
    Write-Host "OK    $Name (healthy)"
    return
  }
  Write-Host "FAIL  $Name ($Container status=$status)"
  $script:fail = 1
}

function Test-ComposeExec([string]$Name, [string]$Service, [string[]]$Command, [string]$Expect = "") {
  $out = & docker compose -f docker-compose.yml -f docker-compose.prod.yml exec -T $Service @Command 2>&1 | Out-String
  if ($LASTEXITCODE -ne 0) {
    Write-Host "FAIL  $Name (compose exec $Service)"
    $script:fail = 1
    return
  }
  if ($Expect -and ($out -notlike "*$Expect*")) {
    Write-Host "FAIL  $Name (compose exec $Service) - unexpected response"
    $script:fail = 1
    return
  }
  Write-Host "OK    $Name"
}

if ($Prod) {
  Write-Host "Healthcheck (prod overlay — Docker health + Caddy interne)"
  foreach ($pair in @(
      @("postgres", "aipack-postgres"),
      @("redis", "aipack-redis"),
      @("minio", "aipack-minio"),
      @("ollama", "aipack-ollama"),
      @("n8n", "aipack-n8n"),
      @("mailpit", "aipack-mailpit"),
      @("backend", "aipack-backend"),
      @("frontend", "aipack-frontend"),
      @("caddy", "aipack-caddy")
    )) {
    Test-ContainerHealth $pair[0] $pair[1]
  }
  Test-ComposeExec "caddy-readyz" "reverse-proxy" @("wget", "-qO-", "http://127.0.0.1:9080/readyz") "ok"
  Test-ComposeExec "backend-actuator" "backend" @("curl", "-fsS", "http://127.0.0.1:8080/actuator/health") "UP"
  $site = Get-EnvValue "PROXY_SITE" "localhost"
  $headers = @{}
  if ($site -and $site -ne ":80") {
    $headers["Host"] = $site
  }
  try {
    $resp = Invoke-WebRequest -Uri "http://127.0.0.1/healthz" -Headers $headers -UseBasicParsing -TimeoutSec 10 -MaximumRedirection 5
    if ($resp.Content -like "*ok*") {
      Write-Host "OK    caddy-host (:80)"
    }
    else {
      Write-Host "FAIL  caddy-host (:80) - unexpected response"
      $fail = 1
    }
  }
  catch {
    Write-Host "WARN  caddy-host (:80) — TLS redirect or port 80 not ready ($($_.Exception.Message))"
  }
}
else {
  $FrontendPort = Get-EnvValue "FRONTEND_PORT" "5173"
  $BackendPort = Get-EnvValue "BACKEND_PORT" "8080"
  $N8nPort = Get-EnvValue "N8N_PORT" "5678"
  $MailpitUi = Get-EnvValue "MAILPIT_UI_PORT" "8025"
  $OllamaPort = Get-EnvValue "OLLAMA_PORT" "11434"
  $MinioPort = Get-EnvValue "MINIO_API_PORT" "9000"

  Write-Host "Healthcheck (host)"
  Test-Endpoint "frontend" "http://127.0.0.1:${FrontendPort}/healthz" "ok"
  Test-Endpoint "backend"  "http://127.0.0.1:${BackendPort}/actuator/health" "UP"
  Test-Endpoint "n8n"      "http://127.0.0.1:${N8nPort}/healthz"
  Test-Endpoint "mailpit"  "http://127.0.0.1:${MailpitUi}/api/v1/info"
  Test-Endpoint "ollama"   "http://127.0.0.1:${OllamaPort}/api/tags"
  Test-Endpoint "minio"    "http://127.0.0.1:${MinioPort}/minio/health/live"
}

if ($fail -ne 0) {
  Write-Error "One or more checks failed."
  exit 1
}
Write-Host "All checks passed."
exit 0
