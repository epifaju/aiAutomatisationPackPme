# Smoke n8n webhooks + Ollama via backend.
# Usage (from repo root):
#   .\scripts\smoke-n8n-ollama.ps1
# Optional: $env:WEBHOOK_SECRET, $env:N8N_PORT, $env:BACKEND_PORT

$ErrorActionPreference = "Stop"
$secret = if ($env:WEBHOOK_SECRET) { $env:WEBHOOK_SECRET } else { "change-me-webhook-secret-min-16-chars" }
$n8nPort = if ($env:N8N_PORT) { $env:N8N_PORT } else { "5678" }
$backendPort = if ($env:BACKEND_PORT) { $env:BACKEND_PORT } else { "8080" }
$companyId = if ($env:DEMO_COMPANY_ID) { $env:DEMO_COMPANY_ID } else { "aaaaaaaa-0000-4000-8000-000000000001" }
$ollamaPort = if ($env:OLLAMA_PORT) { $env:OLLAMA_PORT } else { "11434" }

$headers = @{
  "X-Webhook-Secret" = $secret
  "Content-Type"     = "application/json"
}

function Post-Json($url, $body, $timeoutSec) {
  $json = $body | ConvertTo-Json -Compress -Depth 6
  return Invoke-RestMethod -Uri $url -Method POST -Headers $headers -Body $json -TimeoutSec $timeoutSec
}

Write-Host "== Ollama tags =="
$tags = Invoke-RestMethod -Uri "http://localhost:$ollamaPort/api/tags" -TimeoutSec 10
$model = $tags.models | Where-Object { $_.name -like "llama3.2*" } | Select-Object -First 1
if (-not $model) { throw "Model llama3.2 missing. Run: docker compose up ollama-init" }
Write-Host "OK $($model.name)"

Write-Host "== n8n WF001 email ingest (analyze=false) =="
$msgId = "smoke-n8n-" + [guid]::NewGuid().ToString() + "@aipack.example"
$r1 = Post-Json "http://localhost:$n8nPort/webhook/aipack/email/incoming" @{
  companyId   = $companyId
  messageId   = $msgId
  fromAddress = "smoke@demo.aipack.example"
  toAddress   = "inbox@demo.aipack.example"
  subject     = "Smoke n8n ingestion"
  bodyText    = "Test fiabilisation n8n"
} 60
if ($r1.data.status -ne "RECEIVED") { throw "Expected RECEIVED, got $($r1.data.status)" }
Write-Host "OK id=$($r1.data.id) status=$($r1.data.status)"

Write-Host "== n8n WF010 lead capture (qualify=false) =="
$r2 = Post-Json "http://localhost:$n8nPort/webhook/aipack/leads/create" @{
  companyId = $companyId
  fullName  = "Smoke Lead n8n"
  email     = "smoke.lead." + [guid]::NewGuid().ToString().Substring(0, 8) + "@demo.aipack.example"
  source    = "WEBHOOK"
} 60
Write-Host "OK id=$($r2.data.id) status=$($r2.data.status)"

Write-Host "== Backend + Ollama email analyze (may take 1-3 min on CPU) =="
$msgId2 = "smoke-ai-" + [guid]::NewGuid().ToString() + "@aipack.example"
$sw = [Diagnostics.Stopwatch]::StartNew()
$r3 = Post-Json "http://localhost:$backendPort/webhook/email/incoming" @{
  companyId   = $companyId
  messageId   = $msgId2
  fromAddress = "client@demo.aipack.example"
  toAddress   = "inbox@demo.aipack.example"
  subject     = "Demande de devis automatisation"
  bodyText    = "Bonjour, pouvez-vous envoyer un devis pour automatiser nos emails ?"
  analyze     = $true
} 300
$sw.Stop()
if ($r3.data.status -ne "ANALYZED") { throw "Expected ANALYZED, got $($r3.data.status)" }
if (-not $r3.data.analysis) { throw "Missing analysis (Ollama unavailable?)" }
Write-Host ("OK elapsed={0}ms category={1} approval={2}" -f $sw.ElapsedMilliseconds, $r3.data.analysis.category, $r3.data.analysis.approvalStatus)
Write-Host "summary=$($r3.data.analysis.summary)"

Write-Host ""
Write-Host "Smoke n8n + Ollama: PASS"
Write-Host "n8n UI: http://localhost:$n8nPort"
Write-Host "Tip: after n8n-init, run: docker compose restart n8n"
