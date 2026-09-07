# Restore from an archive produced by backup.ps1 / backup.sh
# Usage: .\scripts\restore.ps1 -BackupDir backups\aipack-backup-... [-RestoreEnv]
param(
  [Parameter(Mandatory = $true)]
  [string]$BackupDir,
  [switch]$RestoreEnv
)

$ErrorActionPreference = "Stop"
$Root = Split-Path -Parent (Split-Path -Parent $MyInvocation.MyCommand.Path)
Set-Location $Root

if (-not (Test-Path $BackupDir)) {
  throw "Backup directory not found: $BackupDir"
}
$Src = (Resolve-Path $BackupDir).Path

function Get-EnvValue([string]$Key, [string]$Default) {
  if (Test-Path ".env") {
    $line = Get-Content ".env" | Where-Object { $_ -match "^$Key=" } | Select-Object -Last 1
    if ($line -match "^$Key=(.+)$") { return $Matches[1].Trim() }
  }
  return $Default
}

function Restore-Volume([string]$Name, [string]$Archive, [string]$Service) {
  $tar = Join-Path $Src $Archive
  if (-not (Test-Path $tar)) {
    Write-Host "-> Skip $Name (no $Archive)"
    return
  }
  $vol = "${Project}_${Name}"
  Write-Host "-> Restore volume $Name"
  $prevEap = $ErrorActionPreference
  $ErrorActionPreference = "SilentlyContinue"
  docker compose stop $Service | Out-Null
  $ErrorActionPreference = $prevEap
  docker volume create $vol 2>$null | Out-Null
  docker run --rm -v "${vol}:/data" -v "${Src}:/backup:ro" alpine:3.20 `
    sh -c "rm -rf /data/* /data/.[!.]* 2>/dev/null; tar xzf /backup/$Archive -C /data"
  if ($LASTEXITCODE -ne 0) { throw "restore volume $Name failed" }
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

Write-Host "==> Restore from $Src"
Write-Host "Databases and n8n/minio volumes will be overwritten. Waiting 5s..."
Start-Sleep -Seconds 5

docker compose up -d postgres --wait
if ($LASTEXITCODE -ne 0) { throw "postgres up failed" }

$dumpAipack = Join-Path $Src "postgres-aipack.dump"
$dumpN8n = Join-Path $Src "postgres-n8n.dump"

if (Test-Path $dumpAipack) {
  Write-Host "-> Restore $PostgresDb"
  docker compose exec -T postgres dropdb -U $PostgresUser --if-exists $PostgresDb
  docker compose exec -T postgres createdb -U $PostgresUser $PostgresDb
  cmd /c "docker compose exec -T postgres pg_restore -U $PostgresUser -d $PostgresDb --no-owner < `"$dumpAipack`""
}

if (Test-Path $dumpN8n) {
  Write-Host "-> Restore $N8nDb"
  docker compose exec -T postgres dropdb -U $PostgresUser --if-exists $N8nDb
  docker compose exec -T postgres createdb -U $PostgresUser $N8nDb
  cmd /c "docker compose exec -T postgres pg_restore -U $PostgresUser -d $N8nDb --no-owner < `"$dumpN8n`""
}

Restore-Volume -Name "n8n_data" -Archive "n8n_data.tar.gz" -Service "n8n"
Restore-Volume -Name "minio_data" -Archive "minio_data.tar.gz" -Service "minio"

$envSecrets = Join-Path $Src "env.secrets"
if ($RestoreEnv -and (Test-Path $envSecrets)) {
  Copy-Item $envSecrets ".env" -Force
  Write-Host "-> .env restored from env.secrets"
}

Write-Host "-> Restart stack"
docker compose up -d postgres n8n ollama mailpit minio redis backend frontend --wait
docker compose up minio-init 2>$null
docker compose restart n8n

Write-Host "Restore done. Verify with .\scripts\healthcheck.ps1"
