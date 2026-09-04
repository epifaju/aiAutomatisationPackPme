# Healthcheck for host-published service ports (from .env)
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

$FrontendPort = Get-EnvValue "FRONTEND_PORT" "5173"
$BackendPort = Get-EnvValue "BACKEND_PORT" "8080"
$N8nPort = Get-EnvValue "N8N_PORT" "5678"
$MailpitUi = Get-EnvValue "MAILPIT_UI_PORT" "8025"
$OllamaPort = Get-EnvValue "OLLAMA_PORT" "11434"
$MinioPort = Get-EnvValue "MINIO_API_PORT" "9000"

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

Write-Host "Healthcheck (host)"
Test-Endpoint "frontend" "http://127.0.0.1:${FrontendPort}/healthz" "ok"
Test-Endpoint "backend"  "http://127.0.0.1:${BackendPort}/actuator/health" "UP"
Test-Endpoint "n8n"      "http://127.0.0.1:${N8nPort}/healthz"
Test-Endpoint "mailpit"  "http://127.0.0.1:${MailpitUi}/api/v1/info"
Test-Endpoint "ollama"   "http://127.0.0.1:${OllamaPort}/api/tags"
Test-Endpoint "minio"    "http://127.0.0.1:${MinioPort}/minio/health/live"

if ($fail -ne 0) {
  Write-Error "One or more checks failed."
  exit 1
}
Write-Host "All checks passed."
exit 0
