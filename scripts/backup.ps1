# Backup PostgreSQL (aipack + n8n) + volumes n8n_data + minio_data
# Usage: .\scripts\backup.ps1 [-OutDir backups] [-IncludeEnv]
param(
  [string]$OutDir = "",
  [switch]$IncludeEnv
)

$ErrorActionPreference = "Stop"
$Root = Split-Path -Parent (Split-Path -Parent $MyInvocation.MyCommand.Path)
Set-Location $Root

if (-not $OutDir) { $OutDir = Join-Path $Root "backups" }
$Stamp = (Get-Date).ToUniversalTime().ToString("yyyyMMddTHHmmssZ")
$Out = Join-Path $OutDir "aipack-backup-$Stamp"
New-Item -ItemType Directory -Force -Path $Out | Out-Null

function Get-EnvValue([string]$Key, [string]$Default) {
  if (Test-Path ".env") {
    $line = Get-Content ".env" | Where-Object { $_ -match "^$Key=" } | Select-Object -Last 1
    if ($line -match "^$Key=(.+)$") { return $Matches[1].Trim() }
  }
  return $Default
}

function Backup-Volume([string]$Name, [string]$Archive, [System.Collections.Generic.List[string]]$Manifest) {
  $vol = "${Project}_${Name}"
  Write-Host "-> Volume $Name"
  docker volume inspect $vol 2>$null | Out-Null
  if ($LASTEXITCODE -eq 0) {
    docker run --rm -v "${vol}:/data:ro" -v "${Out}:/backup" alpine:3.20 `
      tar czf "/backup/$Archive" -C /data .
    if ($LASTEXITCODE -ne 0) { throw "backup volume $Name failed" }
    $Manifest.Add("includes_${Name}=true") | Out-Null
  }
  else {
    Write-Warning "Volume $vol not found - skip $Name"
    $Manifest.Add("includes_${Name}=false") | Out-Null
  }
}

$PostgresUser = Get-EnvValue "POSTGRES_USER" "aipack"
$PostgresDb = Get-EnvValue "POSTGRES_DB" "aipack"
$N8nDb = Get-EnvValue "N8N_DB" "n8n"
$Project = "ai-automation-pack"
try {
  $cfg = docker compose config --format json 2>$null | ConvertFrom-Json
  if ($cfg.name) { $Project = $cfg.name }
}
catch { }

Write-Host "==> Backup to $Out"

$dumpAipack = Join-Path $Out "postgres-aipack.dump"
$dumpN8n = Join-Path $Out "postgres-n8n.dump"

Write-Host "-> PostgreSQL ($PostgresDb)"
cmd /c "docker compose exec -T postgres pg_dump -U $PostgresUser -d $PostgresDb --no-owner --format=custom > `"$dumpAipack`""
if ($LASTEXITCODE -ne 0) { throw "pg_dump aipack failed" }

Write-Host "-> PostgreSQL ($N8nDb)"
cmd /c "docker compose exec -T postgres pg_dump -U $PostgresUser -d $N8nDb --no-owner --format=custom > `"$dumpN8n`""
if ($LASTEXITCODE -ne 0) { throw "pg_dump n8n failed" }

$manifest = [System.Collections.Generic.List[string]]::new()
$manifest.Add("created_at_utc=$Stamp") | Out-Null
$manifest.Add("project=$Project") | Out-Null
$manifest.Add("postgres_db=$PostgresDb") | Out-Null
$manifest.Add("n8n_db=$N8nDb") | Out-Null
$manifest.Add("includes_env=false") | Out-Null

Backup-Volume -Name "n8n_data" -Archive "n8n_data.tar.gz" -Manifest $manifest

Write-Host "-> Pause MinIO for consistent snapshot"
$prevEap = $ErrorActionPreference
$ErrorActionPreference = "SilentlyContinue"
docker compose stop minio | Out-Null
$ErrorActionPreference = $prevEap
try {
  Backup-Volume -Name "minio_data" -Archive "minio_data.tar.gz" -Manifest $manifest
}
finally {
  $ErrorActionPreference = "SilentlyContinue"
  docker compose start minio | Out-Null
  docker compose up -d minio --wait | Out-Null
  $ErrorActionPreference = $prevEap
}

Write-Host "-> Config (no secrets by default)"
Copy-Item ".env.example" (Join-Path $Out "env.example")
Copy-Item "docker-compose.yml" (Join-Path $Out "docker-compose.yml")
if (Test-Path "docker-compose.dev.yml") {
  Copy-Item "docker-compose.dev.yml" (Join-Path $Out "docker-compose.dev.yml")
}
if (Test-Path "proxy") {
  Copy-Item "proxy" (Join-Path $Out "proxy") -Recurse -Force
}

if ($IncludeEnv -and (Test-Path ".env")) {
  Copy-Item ".env" (Join-Path $Out "env.secrets")
  for ($i = 0; $i -lt $manifest.Count; $i++) {
    if ($manifest[$i] -like "includes_env=*") { $manifest[$i] = "includes_env=true" }
  }
  Write-Warning ".env copied in cleartext to env.secrets - protect this archive."
}
else {
  Write-Host "Note: .env not included (use -IncludeEnv to add it)."
}
$manifest | Set-Content (Join-Path $Out "MANIFEST.txt")
Set-Content (Join-Path $OutDir "latest.txt") $Out

Write-Host "Backup done: $Out"
