# n8n

Répertoire des workflows et credentials du pack.

- `workflows/` : exports JSON des automatisations (phases ultérieures).
- `credentials/` : non versionné — aucun secret dans Git.

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

