# Install AI Automation Pack - Windows (PowerShell)
# Usage: .\scripts\install.ps1
$ErrorActionPreference = "Stop"

$Root = Split-Path -Parent (Split-Path -Parent $MyInvocation.MyCommand.Path)
Set-Location $Root

Write-Host "==> AI Automation Pack - installation"

if (-not (Get-Command docker -ErrorAction SilentlyContinue)) {
  throw "Docker is required. Install Docker Desktop."
}

docker compose version | Out-Null
if ($LASTEXITCODE -ne 0) {
  throw "Docker Compose v2 is required (docker compose)."
}

if (-not (Test-Path ".env")) {
  Copy-Item ".env.example" ".env"
  Write-Host "Created .env from .env.example - change secrets before production."
}
else {
  Write-Host ".env already present - keeping it."
}

Write-Host "==> Validate Compose"
docker compose config | Out-Null
if ($LASTEXITCODE -ne 0) { throw "docker compose config failed" }

Write-Host "==> Start services (healthchecks)"
docker compose up -d postgres n8n ollama mailpit minio redis backend frontend --wait
if ($LASTEXITCODE -ne 0) { throw "docker compose up failed" }

Write-Host "==> One-shot inits (MinIO bucket, Ollama model, n8n workflows)"
docker compose up minio-init ollama-init n8n-init
if ($LASTEXITCODE -ne 0) { throw "one-shot init failed" }

Write-Host "==> Restart n8n"
docker compose restart n8n
docker compose up -d n8n --wait

Write-Host "==> Healthcheck"
& "$Root\scripts\healthcheck.ps1"
if ($LASTEXITCODE -ne 0) { throw "healthcheck failed" }

$frontendPort = "5173"
Get-Content ".env" | ForEach-Object {
  if ($_ -match '^FRONTEND_PORT=(.+)$') { $frontendPort = $Matches[1].Trim() }
}

Write-Host ""
Write-Host "Installation complete."
Write-Host "  UI          : http://localhost:$frontendPort"
Write-Host "  Demo login  : demo.admin@aipack.example / DemoAdmin!2026"
Write-Host "  Docs        : docs/installation.md"
