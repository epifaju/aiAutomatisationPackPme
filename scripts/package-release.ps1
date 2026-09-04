# Build a source release archive without secrets or node_modules.
# Usage: .\scripts\package-release.ps1 [-Version 0.1.0]
param(
  [string]$Version = "0.1.0"
)

$ErrorActionPreference = "Stop"
$Root = Split-Path -Parent (Split-Path -Parent $MyInvocation.MyCommand.Path)
Set-Location $Root

$Stamp = (Get-Date).ToUniversalTime().ToString("yyyyMMdd")
$OutDir = Join-Path $Root "dist"
$Name = "aipack-$Version-$Stamp"
$Stage = Join-Path $OutDir $Name
$Archive = Join-Path $OutDir "$Name.zip"

if (Test-Path $Stage) { Remove-Item -Recurse -Force $Stage }
New-Item -ItemType Directory -Force -Path $Stage | Out-Null

$excludeDirs = @(
  ".git", "backups", "dist", "data",
  "backend\target", "frontend\node_modules", "frontend\dist",
  "tests\e2e\node_modules", "tests\e2e\test-results", "tests\e2e\playwright-report"
)

function Should-Skip([string]$FullPath) {
  $rel = $FullPath.Substring($Root.Length).TrimStart("\", "/")
  if ($rel -eq ".env") { return $true }
  if ($rel -like ".env.*" -and $rel -ne ".env.example") { return $true }
  if ($rel -like "n8n\credentials\*" -and -not $rel.EndsWith(".gitkeep")) { return $true }
  if ($rel -like "ollama\models\*" -and -not $rel.EndsWith(".gitkeep")) { return $true }
  if ($rel -like "*.log") { return $true }
  foreach ($d in $excludeDirs) {
    if ($rel -eq $d -or $rel.StartsWith("$d\") -or $rel.StartsWith("$d/")) { return $true }
  }
  return $false
}

Get-ChildItem -Path $Root -Recurse -Force | ForEach-Object {
  if ($_.FullName.StartsWith($OutDir)) { return }
  if (Should-Skip $_.FullName) { return }
  $rel = $_.FullName.Substring($Root.Length).TrimStart("\", "/")
  $dest = Join-Path $Stage $rel
  if ($_.PSIsContainer) {
    New-Item -ItemType Directory -Force -Path $dest | Out-Null
  }
  else {
    $parent = Split-Path $dest -Parent
    if (-not (Test-Path $parent)) { New-Item -ItemType Directory -Force -Path $parent | Out-Null }
    Copy-Item $_.FullName $dest -Force
  }
}

Copy-Item (Join-Path $Root ".env.example") (Join-Path $Stage ".env.example") -Force
New-Item -ItemType Directory -Force -Path (Join-Path $Stage "n8n\credentials") | Out-Null
New-Item -ItemType Directory -Force -Path (Join-Path $Stage "ollama\models") | Out-Null
New-Item -ItemType File -Force -Path (Join-Path $Stage "n8n\credentials\.gitkeep") | Out-Null
New-Item -ItemType File -Force -Path (Join-Path $Stage "ollama\models\.gitkeep") | Out-Null

if (Test-Path $Archive) { Remove-Item -Force $Archive }
Compress-Archive -Path $Stage -DestinationPath $Archive -Force

Write-Host "Release package: $Archive"
Write-Host "Contents: sources + workflows + docs + scripts (no secrets)."
