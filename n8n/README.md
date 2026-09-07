# n8n

Répertoire des workflows et credentials du pack.

- `workflows/` : JSON versionnés (PRD §20), importés par `n8n-init` au `docker compose up`.
- `credentials/` : non versionné — aucun secret dans Git.
- `build-workflows.mjs` : régénère les JSON (`node n8n/build-workflows.mjs`).
- `import.sh` : `n8n import:workflow` + activation (IDs stables, upsert idempotent).

## Mise en service

```bash
docker compose up -d n8n --wait
docker compose up n8n-init
docker compose restart n8n   # obligatoire pour enregistrer webhooks / schedules
```

Smoke (PowerShell, depuis la racine) :

```powershell
$env:N8N_PORT="5677"          # si ports décalés dans .env
$env:BACKEND_PORT="18080"
.\scripts\smoke-n8n-ollama.ps1
```

Les workflows lisent `WEBHOOK_SECRET`, `BACKEND_BASE_URL` (`http://backend:8080`) et `DEMO_COMPANY_ID` depuis l’environnement n8n (`N8N_BLOCK_ENV_ACCESS_IN_NODE=false`). Aucun secret dans les JSON.

Webhooks n8n (header `X-Webhook-Secret` obligatoire) :

```text
POST /webhook/aipack/email/incoming     WF001  analyze=false
POST /webhook/aipack/email/analyze      WF002  analyze=true
POST /webhook/aipack/email/reply        WF003  analyze=true, jamais d’envoi auto
POST /webhook/aipack/leads/create       WF010  qualify=false
POST /webhook/aipack/leads/qualify      WF011  qualify=true
POST /webhook/aipack/documents/ingest   WF020  process=false
POST /webhook/aipack/documents/process  WF021  process=true
POST /webhook/aipack/invoices/reminder  WF031
POST /webhook/aipack/audit              WF090
```

Pièces jointes email (WF001/002) : le body peut inclure `attachments: [{ filename, contentType?, contentBase64 }]` (max 10, PDF/PNG/JPEG/TXT). Le JSON n8n retransmet le body tel quel vers le backend (`...body`). Stockage MinIO sous `{companyId}/emails/{emailId}/…`.

Planifiés (Europe/Paris) : WF030 relances 08:00 · WF040 rapport 07:30.

**WF091 Error Handler** doit rester **inactif** (pas de toggle Active). n8n le déclenche uniquement quand un autre workflow échoue (`settings.errorWorkflow`). L’activer provoque l’erreur *« no node to start the workflow »* / triangle rouge. En cas d’échec, WF091 sanitize le payload puis appelle `POST /webhook/audit/n8n-error` (audit `ERROR` / `N8N_WORKFLOW_ERROR`).

UI n8n : `http://localhost:${N8N_PORT}` (défaut 5678).

Backend (Phase 6), pour WF010 / WF011 :

```text
POST /webhook/leads/create
Header : X-Webhook-Secret
```

Backend (Phase 7), pour WF001 / WF002 / WF003 :

```text
POST /webhook/email/incoming
Header : X-Webhook-Secret

POST /api/v1/emails/{id}/analyze
POST /api/v1/emails/{id}/approve
POST /api/v1/emails/{id}/reject
POST /api/v1/emails/{id}/send
```

Les réponses générées par IA ne sont pas envoyées tant que `AI_GENERATED_EMAIL_AUTO_SEND=false` (défaut). L’envoi passe par SMTP (`SMTP_HOST`, Mailpit en développement).

Backend (Phase 8), pour WF020 / WF021 :

```text
POST /webhook/documents/process
Header : X-Webhook-Secret

POST /api/v1/documents                    (multipart file)
POST /api/v1/documents/{id}/process
POST /api/v1/documents/{id}/approve
POST /api/v1/documents/{id}/reject
```

Les fichiers sont stockés dans MinIO (`MINIO_*`). L’extraction IA n’écrit pas le document en `EXTRACTED` si `confidenceScore` < seuil entreprise.

Backend (Phase 9), pour WF030 / WF031 :

```text
POST /webhook/invoices/reminder
Header : X-Webhook-Secret

POST /api/v1/invoices/overdue/detect
POST /api/v1/invoices/{id}/reminders/{level}/approve
POST /api/v1/invoices/{id}/reminders/{level}/reject
POST /api/v1/invoices/{id}/reminders/{level}/send
```

Les relances passent par SMTP (`SMTP_HOST`, Mailpit en développement). Elles ne partent pas tant que `INVOICE_REMINDER_AUTO_SEND=false`. Une facture `PAID` n’est jamais relancée. J+30 reste une alerte manuelle.

Backend (Phase 10), pour WF040 :

```text
POST /webhook/reports/daily
Header : X-Webhook-Secret

GET  /api/v1/dashboard/summary
POST /api/v1/reports/daily
POST /api/v1/reports/daily/{id}/send
```

Le rapport quotidien passe par SMTP (`SMTP_HOST`, Mailpit en développement). Il n’est pas auto-envoyé tant que `DAILY_REPORT_AUTO_SEND=false`.

Le secret est `WEBHOOK_SECRET` (voir `.env.example`). Aucun credential dans les JSON exportés.

n8n persiste dans PostgreSQL (base `n8n` sur le même instance que l’application).
