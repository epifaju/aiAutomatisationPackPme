# Configuration

## Fichier `.env`

Copié depuis `.env.example`. **Ne jamais committer `.env`.**

| Groupe | Variables clés | Notes |
| --- | --- | --- |
| Postgres | `POSTGRES_*` | Mot de passe à changer hors démo |
| Auth | `JWT_SECRET` | ≥ 32 caractères |
| Webhooks | `WEBHOOK_SECRET` | Header `X-Webhook-Secret` |
| Rate limit | `RATE_LIMIT_*` | Bucket4j login/refresh + webhooks (voir [security.md](security.md)) |
| Documents / OCR | `DOCUMENT_OCR_*` | Tesseract fra+eng dans l’image backend |
| ClamAV | `CLAMAV_*` | Antivirus optionnel (profile Compose `clamav`) — [security.md](security.md) |
| Reverse proxy | `PROXY_*` | Caddy optionnel — [proxy.md](proxy.md) |
| n8n | `N8N_ENCRYPTION_KEY`, `N8N_PORT`, `N8N_WEBHOOK_URL` | Clé stable (chiffrement credentials n8n) |
| IA | `AI_PROVIDER`, `OLLAMA_*`, `AI_OPENAI_*`, `AI_TIMEOUT` | `ollama` (défaut) ou `openai` (Chat Completions) |
| Emails / factures / rapports | `*_AUTO_SEND` | `false` par défaut (validation humaine) |
| MinIO | `MINIO_ACCESS_KEY`, `MINIO_SECRET_KEY` | Changer en production |
| SMTP | `SMTP_HOST`, `MAILPIT_*` | Mailpit en développement |

Les ports `*_PORT` concernent la publication **hôte**. Les services se parlent entre eux via les noms Compose (`postgres:5432`, `mailpit:1025`, …).

## Profils de sécurité métier

- Réponses email IA : jamais envoyées si `AI_GENERATED_EMAIL_AUTO_SEND=false` (validation Inbox).
- Relances facture : `INVOICE_REMINDER_AUTO_SEND=false` ; J+30 toujours manuel.
- Rapport quotidien : `DAILY_REPORT_AUTO_SEND=false` par défaut.

## Frontend local (hors Compose)

```bash
cd frontend && npm install && npm run dev
```

Vite proxifie `/api` vers `http://localhost:$BACKEND_PORT` (surcharge : `API_PROXY_TARGET`).

## Sauvegarde / restauration

### Backup

```bash
./scripts/backup.sh
# INCLUDE_ENV=1 ./scripts/backup.sh   # inclut .env (secrets en clair — à protéger)
```

```powershell
.\scripts\backup.ps1
.\scripts\backup.ps1 -IncludeEnv
```

Contenu typique d’un backup :

- `postgres-aipack.dump` / `postgres-n8n.dump`
- `n8n_data.tar.gz` (volume workflows / credentials chiffrés n8n)
- `minio_data.tar.gz` (fichiers documents MinIO — snapshot cohérent, MinIO est mis en pause brièvement)
- `env.example`, `docker-compose.yml`, `MANIFEST.txt`
- optionnel : `env.secrets`

Les archives sont écrites sous `backups/` (gitignoré).

### Restore

```bash
./scripts/restore.sh backups/aipack-backup-YYYYMMDDTHHMMSSZ
RESTORE_ENV=1 ./scripts/restore.sh ...   # si env.secrets présent
```

```powershell
.\scripts\restore.ps1 -BackupDir backups\aipack-backup-...
.\scripts\restore.ps1 -BackupDir ... -RestoreEnv
```

Après restore, `minio-init` est relancé pour s’assurer que le bucket applicatif existe.

## Package release

```bash
./scripts/package-release.sh 0.1.0
```

```powershell
.\scripts\package-release.ps1 -Version 0.1.0
```

Produit `dist/aipack-<version>-<date>.zip` sans secrets ni `node_modules`.
