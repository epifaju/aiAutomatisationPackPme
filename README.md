# AI Automation Pack for TPE/PME

Pack d’automatisation IA **self-hosted** pour TPE/PME françaises : n8n, IA locale (Ollama) ou API externe, emails, prospects, documents, factures, rapports et audit — installable via Docker Compose.

**Statut actuel : Phase 13 — Packaging.** CI GitHub Actions, scripts install/backup/restore, documentation `docs/`, package release. M01–M06 + frontend MVP + E2E Playwright. MVP packaging complet selon PRD §13 / §40bis.

Le PRD `PRD___AI_Automation_Pack_for_TPE-PME_v1.1.md` est la source de vérité du projet.

## Objectif d’installation (cible MVP)

```bash
./scripts/install.sh          # Linux / macOS / Git Bash
# ou
.\scripts\install.ps1         # Windows PowerShell
```

Équivalent manuel : `docker compose up -d` (+ inits). Voir [docs/installation.md](docs/installation.md).

## Stack imposée (PRD §5)

| Couche | Choix | Image pinée |
| --- | --- | --- |
| Orchestration | Docker Compose | — |
| Automatisation | n8n (PostgreSQL) | `n8nio/n8n:1.107.4` |
| Base de données | PostgreSQL 16 + pgvector | `pgvector/pgvector:0.8.6-pg16` |
| IA locale | Ollama | `ollama/ollama:0.11.10` |
| Backend | Spring Boot 3.5.5 / Java 21 / Maven | build local `backend/Dockerfile` |
| Frontend | React / TypeScript / Vite / Tailwind | build local `frontend/Dockerfile` |
| Emails de test | Mailpit | `axllent/mailpit:v1.27.4` |
| Stockage objet | MinIO | `minio/minio:RELEASE.2025-07-23T15-54-02Z` |
| Cache | Redis (présent, non bloquant) | `redis:7.4-alpine` |

La stack FastAPI n’est pas retenue.

## Démarrage

1. Copier les variables d’environnement :

```bash
cp .env.example .env
```

Sous PowerShell :

```powershell
Copy-Item .env.example .env
```

Adapter les ports hôte dans `.env` s’ils sont déjà utilisés (PostgreSQL local, autre stack Docker, etc.).

2. Valider Compose :

```bash
docker compose config
```

3. Démarrer l’infrastructure, le backend et le frontend :

```bash
docker compose up -d
```

Attendre les healthchecks (sans le one-shot MinIO) :

```bash
docker compose up -d postgres n8n ollama mailpit minio redis backend frontend --wait
docker compose up minio-init ollama-init n8n-init
docker compose restart n8n
```

4. Contrôler :

```bash
docker compose ps
```

L’UI est servie sur le port `FRONTEND_PORT` (5173). Nginx proxifie `/api/` vers le backend (même origine, pas de CORS à configurer en Compose).

Développement local du frontend (Vite proxifie `/api` vers `http://localhost:$BACKEND_PORT`, surcharge possible avec `API_PROXY_TARGET`) :

```bash
cd frontend
npm install
npm run dev
npm test
```

Compiler et tester le backend (Java 21) :

```bash
mvn -f backend/pom.xml verify
```

Tests E2E Playwright (Phase 12) — stack réelle démarrée :

```bash
cd tests/e2e
npm install
npx playwright install chromium
npm test
```

Détails : `tests/e2e/README.md`.

## Ports par défaut (hôte)

| Service | Port hôte | URL / usage |
| --- | --- | --- |
| PostgreSQL | `POSTGRES_PORT` (5432) | `localhost:5432` — les conteneurs parlent à `postgres:5432` |
| Frontend | `FRONTEND_PORT` (5173) | http://localhost:5173 — dashboard React |
| Backend | `BACKEND_PORT` (8080) | http://localhost:8080 → Swagger UI · http://localhost:8080/actuator/health |
| n8n | `N8N_PORT` (5678) | http://localhost:5678 |
| Mailpit SMTP | `MAILPIT_SMTP_HOST_PORT` (1025) | SMTP de test |
| Mailpit UI | `MAILPIT_UI_PORT` (8025) | http://localhost:8025 |
| MinIO API | `MINIO_API_PORT` (9000) | http://localhost:9000 |
| MinIO Console | `MINIO_CONSOLE_PORT` (9001) | http://localhost:9001 |
| Redis | `REDIS_PORT` (6379) | cache |
| Ollama | `OLLAMA_PORT` (11434) | http://localhost:11434 |

`SMTP_HOST` / `SMTP_PORT` restent l’adresse **interne** (`mailpit:1025`) utilisée par les futurs services applicatifs.

## Healthchecks

- PostgreSQL : `pg_isready` + extension `vector` + bases `aipack` et `n8n`
- Backend : `GET /actuator/health` → `UP` (Flyway `V1`–`V9` + seed `R__demo_data`)
- n8n : `GET /healthz`
- Mailpit : `GET /api/v1/info` + SMTP
- Ollama : `GET /api/tags` · `ollama-init` tire `llama3.2`
- MinIO : `GET /minio/health/live` + bucket `aipack`
- Frontend : `GET /healthz` → `ok`
- Redis : `PING`

OpenAPI : `GET /v3/api-docs` · Swagger UI : `/swagger-ui.html`.

`ollama-init` tire le modèle `${OLLAMA_MODEL}` (défaut `llama3.2`, ~2 Go) puis fait un warm-up. `OLLAMA_KEEP_ALIVE=-1` garde le modèle en mémoire. Premier inferencing CPU : souvent 30–120 s (`AI_TIMEOUT=120s` par appel).

Smoke n8n + Ollama :

```powershell
$env:N8N_PORT="5677"       # adapter si besoin
$env:BACKEND_PORT="18080"
.\scripts\smoke-n8n-ollama.ps1
```

## Services Compose

| Service | Rôle | Profile | Phase |
| --- | --- | --- | --- |
| `postgres` | PostgreSQL + pgvector | défaut | 1 |
| `n8n` | Automatisation | défaut | 1 |
| `n8n-init` | Import des workflows JSON | défaut (one-shot) | 1 |
| `ollama` | IA locale | défaut | 1 |
| `ollama-init` | `ollama pull` du modèle | défaut (one-shot) | 1 |
| `mailpit` | SMTP / UI mails de test | défaut | 1 |
| `minio` | Stockage objet S3 | défaut | 1 |
| `minio-init` | Création du bucket (one-shot) | défaut | 1 |
| `redis` | Cache / files légères | défaut | 1 |
| `backend` | API Spring Boot | défaut | 2 |
| `frontend` | Dashboard React | défaut | 11 |
| `reverse-proxy` | Caddy (TLS / entrée unique) | `proxy` (off) | V2 / prod |

Le reverse proxy Caddy est **optionnel** (`--profile proxy`) — voir [docs/proxy.md](docs/proxy.md). Redis n’est une dépendance d’aucun autre service : le reste du stack démarre même si Redis est arrêté.

## Arborescence

```text
ai-automation-pack/
├── README.md
├── LICENSE
├── PRD___AI_Automation_Pack_for_TPE-PME_v1.1.md
├── docker-compose.yml
├── docker-compose.dev.yml
├── .env.example
├── .gitignore
├── .gitattributes
│
├── .github/workflows/          # backend-ci, frontend-ci, e2e
├── backend/
│   ├── pom.xml
│   ├── Dockerfile
│   └── src/                    # Spring Boot — Phase 2
├── frontend/
│   ├── package.json
│   ├── Dockerfile
│   └── src/                    # React — Phase 11
├── database/
│   ├── init/                   # init Postgres (pgvector, base n8n)
│   ├── migrations/             # Flyway V1–V9 (baseline, identity, domain, refresh tokens, leads IA, emails IA, documents IA, relances, rapports)
│   └── seed/                   # R__demo_data.sql
├── n8n/
│   ├── workflows/
│   ├── credentials/            # gitignoré
│   └── README.md
├── ollama/
│   ├── models/                 # gitignoré (poids)
│   └── prompts/                # prompts IA (hors code)
├── proxy/                      # Caddy (profile Compose proxy)
├── scripts/                    # install, healthcheck, backup, restore, package-release
├── docs/                       # installation, config, architecture, security, …
└── tests/
    ├── integration/
    └── e2e/
```

## Données de démonstration

Seed Flyway repeatable `database/seed/R__demo_data.sql` (inserts idempotents, données fictives) :

| Entité | Quantité |
| --- | --- |
| Entreprise | 1 (`Demo SAS`) |
| Admin | 1 (`demo.admin@aipack.example`) |
| Leads | 10 |
| Emails | 10 |
| Factures | 10 |
| Documents | 5 |
| Audit logs | 20 |

Compte de démo (profils `dev` / `test` uniquement — le hash seed `pgcrypto` est réécrit en BCrypt Spring au démarrage) :

```text
email    : demo.admin@aipack.example
password : DemoAdmin!2026
```

Ce compte ne doit pas servir en production. `JWT_SECRET` doit faire au moins 32 caractères (voir `.env.example`).

## Authentification (Phase 4)

Enveloppe `ApiResponse` sur login, refresh et `/me`. Logout répond `204`.

| Méthode | Chemin | Auth |
| --- | --- | --- |
| `POST` | `/api/v1/auth/login` | public |
| `POST` | `/api/v1/auth/refresh` | public (refresh token dans le body) |
| `POST` | `/api/v1/auth/logout` | `Authorization: Bearer <access>` |
| `GET` | `/api/v1/auth/me` | `Authorization: Bearer <access>` |

Access token : **15 min**. Refresh : **7 jours**, rotatif, hash SHA-256 en base. Réutiliser un refresh déjà révoqué invalide tous les refresh actifs de l’utilisateur.

```bash
curl -s -X POST http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"demo.admin@aipack.example","password":"DemoAdmin!2026"}'

curl -s http://localhost:8080/api/v1/auth/me \
  -H "Authorization: Bearer <accessToken>"
```

`GET /` (backend), health, OpenAPI et Swagger restent publics. Le dashboard utilisateur est le frontend React (`http://localhost:5173`).

## Frontend (Phase 11)

Stack : React 19, Vite, TypeScript, Tailwind CSS, TanStack Query, React Router, Zustand, React Hook Form + Zod. Composants UI cohérents (cartes, badges, formulaires) — pas de clé API dans le navigateur.

Pages : Login, Dashboard, Inbox, Leads, Documents, Invoices, Automations, Audit, Settings.

Compte démo : `demo.admin@aipack.example` / `DemoAdmin!2026`.

| Méthode | Chemin | Auth |
| --- | --- | --- |
| `GET` / `PUT` | `/api/v1/settings` | JWT |
| `GET` | `/api/v1/automations` | JWT |
| `POST` | `/api/v1/automations/{id}/run` | JWT |
| `POST` | `/api/v1/automations/{id}/auto-send` | JWT |

`Run` est disponible pour les relances (`overdue/detect`) et le rapport du jour. Emails / leads / documents se lancent depuis leur page. L’auto-envoi entreprise reste inerte tant que la variable d’environnement correspondante est à `false`.

## Audit (Phase 5)

Journal append-only pour les automatisations (PRD §15). Les secrets (`password`, clé API, access/refresh token, `secret`) sont retirés des métadonnées avant écriture.

| Méthode | Chemin | Auth |
| --- | --- | --- |
| `GET` | `/api/v1/audit` | `Authorization: Bearer <access>` |

Filtres optionnels : `workflow`, `action`, `entityType`, `entityId`, `status` (`SUCCESS` \| `ERROR` \| `SKIPPED`), `from`, `to`. Pagination Spring (`page`, `size`, tri par `createdAt` desc). Les résultats sont limités à l’entreprise du jeton.

Login et logout écrivent une entrée (`LOGIN` / `LOGOUT`, `entityType=USER`). Les modules métier appellent `AuditService.record(...)`.

```bash
curl -s "http://localhost:8080/api/v1/audit?size=20" \
  -H "Authorization: Bearer <accessToken>"
```

## Leads (Phase 6)

CRUD scoped à l’entreprise du jeton. Le webhook public est authentifié par `X-Webhook-Secret` (`WEBHOOK_SECRET`). La qualification IA passe par `AIProvider` (Ollama), avec retry, circuit breaker, cache Redis optionnel (fail-open) et prompt `ollama/prompts/lead-qualification.txt`.

| Méthode | Chemin | Auth |
| --- | --- | --- |
| `GET` | `/api/v1/leads` | JWT |
| `GET` | `/api/v1/leads/{id}` | JWT |
| `POST` | `/api/v1/leads` | JWT |
| `POST` | `/api/v1/leads/import` | JWT (multipart `file` CSV) |
| `PUT` | `/api/v1/leads/{id}` | JWT |
| `DELETE` | `/api/v1/leads/{id}` | JWT |
| `POST` | `/api/v1/leads/{id}/qualify` | JWT |
| `POST` | `/webhook/leads/create` | `X-Webhook-Secret` |

Filtres liste : `status`, `source`, `q` (nom / email / société). Pagination Spring (`page`, `size`, tri `createdAt` desc).

Pipeline : `NEW` → `QUALIFIED` → `CONTACTED` → `PROPOSAL` → `WON` / `LOST`. Sources : `WEB_FORM`, `WEBHOOK`, `EMAIL`, `CSV`, `API`.

Import CSV (`POST /api/v1/leads/import`) : UTF-8, séparateur `,` ou `;`, max 500 lignes. Colonnes : `fullName` (obligatoire ; aliases `nom`, `name`) ; optionnel `email`, `companyName`/`societe`, `phone`/`telephone`, `summary`/`notes`. Source forcée `CSV`, statut `NEW`. Succès partiel possible (lignes invalides listées dans `errors`).

Score 0–100 (bandes configurables : 0–30 faible, 31–60 moyen, 61–80 intéressant, 81–100 prioritaire). Si `confidenceScore` < seuil entreprise (0,700 par défaut) → `aiStatus=REVIEW_REQUIRED` (le statut pipeline n’est pas avancé). Parsing JSON impossible → `AI_PARSING_ERROR`. Ollama indisponible → `AI_UNAVAILABLE` (le lead est tout de même créé).

Le webhook crée le lead (`source=WEBHOOK`) puis lance la qualification sauf si `"qualify": false`. Chaque création / mise à jour / suppression / qualification écrit un `lead_event` et une entrée d’audit (`entityType=LEAD`).

```bash
curl -s -X POST http://localhost:8080/api/v1/leads \
  -H "Authorization: Bearer <accessToken>" \
  -H "Content-Type: application/json" \
  -d '{"fullName":"Marie Dupont","email":"marie.dupont@demo.aipack.example","source":"WEB_FORM"}'

curl -s -X POST http://localhost:8080/webhook/leads/create \
  -H "X-Webhook-Secret: $WEBHOOK_SECRET" \
  -H "Content-Type: application/json" \
  -d '{"companyId":"aaaaaaaa-0000-4000-8000-000000000001","fullName":"Paul Martin","email":"paul.martin@demo.aipack.example"}'
```

## Rapports (Phase 10)

Rapport quotidien scoped à l’entreprise. Un scheduler (07:30 Europe/Paris) génère le rapport de **la veille** ; l’API JWT génère **aujourd’hui** par défaut. La synthèse IA utilise `AIProvider` et le prompt `ollama/prompts/daily-report.txt`. Si Ollama est indisponible ou le JSON illisible, un texte modèle est conservé (fail-open). L’envoi email n’est jamais automatique tant que `DAILY_REPORT_AUTO_SEND` **et** `company_settings.daily_report_auto_send` restent à `false`. Destinataire : `daily_report_email`, sinon le premier ADMIN, puis le premier utilisateur actif.

| Méthode | Chemin | Auth |
| --- | --- | --- |
| `GET` | `/api/v1/dashboard/summary` | JWT |
| `GET` | `/api/v1/reports/daily` | JWT |
| `GET` | `/api/v1/reports/daily/{id}` | JWT |
| `POST` | `/api/v1/reports/daily` | JWT |
| `POST` | `/api/v1/reports/daily/{id}/send` | JWT |
| `POST` | `/webhook/reports/daily` | `X-Webhook-Secret` |

Filtres liste : `date`. Pagination Spring (`page`, `size`, tri `reportDate` desc). Un seul rapport par `(companyId, reportDate)` (idempotent). Statuts : `GENERATED`, `SENT`, `ERROR`.

`GET /api/v1/dashboard/summary` : métriques du jour (fuseau entreprise) + 10 derniers événements d’audit. Indicateurs : emails reçus/urgents, nouveaux/prioritaires leads, documents traités/erreur, factures en retard + montant, relances envoyées, automatisations OK/erreur.

`POST /api/v1/reports/daily` : `{ "date": "2026-09-01", "send": false }`. `"send": true` envoie explicitement (Mailpit en développement).

```bash
curl -s http://localhost:8080/api/v1/dashboard/summary \
  -H "Authorization: Bearer <accessToken>"

curl -s -X POST http://localhost:8080/api/v1/reports/daily \
  -H "Authorization: Bearer <accessToken>" \
  -H "Content-Type: application/json" \
  -d '{"date":"2026-09-01"}'

curl -s -X POST http://localhost:8080/webhook/reports/daily \
  -H "X-Webhook-Secret: $WEBHOOK_SECRET" \
  -H "Content-Type: application/json" \
  -d '{"companyId":"aaaaaaaa-0000-4000-8000-000000000001","date":"2026-09-01"}'
```

## Factures (Phase 9)

CRUD clients et factures, scoped à l’entreprise. Un scheduler quotidien (08:00 Europe/Paris) et le webhook n8n marquent les factures `SENT` en `OVERDUE` puis créent les relances dues (J+3 cordial, J+7, J+15 ferme, J+30 alerte manuelle). Idempotence sur `(invoice_id, reminder_level)` (PRD §34). Aucune relance si `status=PAID` (ni `CANCELLED` / `DRAFT`). J+30 n’est **jamais** auto-envoyé. Les autres niveaux non plus tant que `INVOICE_REMINDER_AUTO_SEND` **et** `company_settings.invoice_reminder_auto_send` restent à `false`.

| Méthode | Chemin | Auth |
| --- | --- | --- |
| `GET` / `POST` | `/api/v1/customers` | JWT |
| `GET` / `PUT` / `DELETE` | `/api/v1/customers/{id}` | JWT |
| `GET` / `POST` | `/api/v1/invoices` | JWT |
| `GET` / `PUT` | `/api/v1/invoices/{id}` | JWT |
| `POST` | `/api/v1/invoices/overdue/detect` | JWT |
| `POST` | `/api/v1/invoices/{id}/reminders/{level}/approve` | JWT |
| `POST` | `/api/v1/invoices/{id}/reminders/{level}/reject` | JWT |
| `POST` | `/api/v1/invoices/{id}/reminders/{level}/send` | JWT |
| `POST` | `/webhook/invoices/reminder` | `X-Webhook-Secret` |

Filtres factures : `status`, `customerId`, `q` (numéro). Pagination Spring (`page`, `size`, tri `createdAt` desc). Statuts : `DRAFT`, `SENT`, `PAID`, `OVERDUE`, `CANCELLED`.

```bash
curl -s -X POST http://localhost:8080/api/v1/invoices \
  -H "Authorization: Bearer <accessToken>" \
  -H "Content-Type: application/json" \
  -d '{"customerId":"dddddddd-0000-4000-8000-000000000001","invoiceNumber":"F-2026-100","invoiceDate":"2026-08-01","dueDate":"2026-08-15","amountExcludingTax":100,"vat":20,"amountIncludingTax":120,"status":"SENT"}'

curl -s -X POST http://localhost:8080/webhook/invoices/reminder \
  -H "X-Webhook-Secret: $WEBHOOK_SECRET" \
  -H "Content-Type: application/json" \
  -d '{"companyId":"aaaaaaaa-0000-4000-8000-000000000001"}'
```

## Documents (Phase 8)

Upload scoped à l’entreprise, stockage objet MinIO (jamais en `bytea`). L’extraction de texte passe par Apache Tika (PDF, PNG, JPEG, TXT). Le parsing IA utilise `AIProvider` et le prompt `ollama/prompts/document-extraction.txt`. MIME contrôlé par **magic bytes** (Tika), pas seulement l’extension. Taille max : 20 Mo. Un même fichier (checksum SHA-256) n’est pas recréé.

Si `confidenceScore` < seuil entreprise (`document_confidence_threshold`, 0,700 par défaut) → `REVIEW_REQUIRED`. OCR (Tesseract `fra+eng` dans l’image backend) : PDF/PNG/JPEG scannés sont OCRisés si peu de texte embarqué ; si l’OCR reste vide → revue humaine, l’IA n’est pas appelée. Parsing JSON impossible → `ERROR` / `AI_PARSING_ERROR`. Ollama indisponible → `ERROR` / `AI_UNAVAILABLE`. Variables : `DOCUMENT_OCR_ENABLED`, `DOCUMENT_OCR_LANGUAGES`, `DOCUMENT_OCR_MIN_CHARS`.

| Méthode | Chemin | Auth |
| --- | --- | --- |
| `GET` | `/api/v1/documents` | JWT |
| `GET` | `/api/v1/documents/{id}` | JWT |
| `POST` | `/api/v1/documents` | JWT (multipart `file`) |
| `POST` | `/api/v1/documents/{id}/process` | JWT |
| `POST` | `/api/v1/documents/{id}/approve` | JWT |
| `POST` | `/api/v1/documents/{id}/reject` | JWT |
| `POST` | `/webhook/documents/process` | `X-Webhook-Secret` |

Filtres liste : `status`, `documentType`, `q` (nom de fichier). Pagination Spring (`page`, `size`, tri `createdAt` desc).

Statuts document : `UPLOADED` → `PROCESSING` → `EXTRACTED` / `REVIEW_REQUIRED` / `ERROR`. Types : `FACTURE`, `DEVIS`, `BON_COMMANDE`, `CONTRAT`, `COURRIER`, `AUTRE`.

`POST /api/v1/documents` : champ `file` + `process` (défaut `true`) + `documentType` optionnel. Le webhook accepte soit un `documentId` existant, soit `originalFilename` + `contentBase64`. `"process": false` stocke sans appeler l’IA.

```bash
curl -s -X POST http://localhost:8080/api/v1/documents \
  -H "Authorization: Bearer <accessToken>" \
  -F "file=@./facture.txt;type=text/plain" \
  -F "documentType=FACTURE"

curl -s -X POST http://localhost:8080/webhook/documents/process \
  -H "X-Webhook-Secret: $WEBHOOK_SECRET" \
  -H "Content-Type: application/json" \
  -d '{"companyId":"aaaaaaaa-0000-4000-8000-000000000001","documentId":"<id>"}'
```

## Emails (Phase 7)

Ingestion scoped à l’entreprise. Le webhook public est authentifié par `X-Webhook-Secret`. L’analyse IA (classification, priorité, intention, résumé, suggestion de réponse) passe par `AIProvider`, avec les prompts `ollama/prompts/email-classification.txt` et `email-response.txt`. Une réponse n’est **jamais** envoyée sans validation humaine, et jamais automatiquement tant que `AI_GENERATED_EMAIL_AUTO_SEND` (env) **et** `company_settings.ai_generated_email_auto_send` sont à `false` (défaut PRD §10).

| Méthode | Chemin | Auth |
| --- | --- | --- |
| `GET` | `/api/v1/emails` | JWT |
| `GET` | `/api/v1/emails/{id}` | JWT |
| `POST` | `/api/v1/emails/{id}/analyze` | JWT |
| `POST` | `/api/v1/emails/{id}/approve` | JWT |
| `POST` | `/api/v1/emails/{id}/reject` | JWT |
| `POST` | `/api/v1/emails/{id}/send` | JWT |
| `POST` | `/webhook/email/incoming` | `X-Webhook-Secret` |

Filtres liste : `status`, `category`, `priority`, `approvalStatus`, `q` (sujet / expéditeur / destinataire). Pagination Spring (`page`, `size`, tri `receivedAt` desc).

Statuts email : `RECEIVED` → `ANALYZED` / `ERROR`. Catégories : `CLIENT`, `PROSPECT`, `FACTURE`, `FOURNISSEUR`, `SUPPORT`, `ADMINISTRATIF`, `SPAM`, `AUTRE`. Priorités : `LOW`, `NORMAL`, `HIGH`, `URGENT`.

Si `confidenceScore` < seuil entreprise (0,700 par défaut) → `analysis.status=REVIEW_REQUIRED`. Parsing JSON impossible → email `ERROR` (`AI_PARSING_ERROR` en audit). Ollama indisponible → email `ERROR`. Le spam n’a pas de suggestion de réponse ni de file d’approbation. Sinon `approvalStatus=PENDING_APPROVAL` jusqu’à `APPROVED` / `REJECTED` / `EXECUTED`.

Le webhook est idempotent sur `(companyId, messageId)`. `"analyze": false` enregistre l’email sans appeler l’IA. `POST .../send` n’est possible qu’après `APPROVED` (envoi vers Mailpit en développement).

```bash
curl -s -X POST http://localhost:8080/webhook/email/incoming \
  -H "X-Webhook-Secret: $WEBHOOK_SECRET" \
  -H "Content-Type: application/json" \
  -d '{"companyId":"aaaaaaaa-0000-4000-8000-000000000001","messageId":"<unique@aipack.example>","fromAddress":"marie.dupont@demo.aipack.example","toAddress":"inbox@demo.aipack.example","subject":"Demande de devis","bodyText":"Pouvez-vous envoyer un devis ?"}'

curl -s -X POST http://localhost:8080/api/v1/emails/<id>/approve \
  -H "Authorization: Bearer <accessToken>"

curl -s -X POST http://localhost:8080/api/v1/emails/<id>/send \
  -H "Authorization: Bearer <accessToken>"
```

## Principes (PRD §4)

1. Self-hosted par défaut
2. Architecture Docker
3. Configuration par variables d’environnement
4. PostgreSQL comme stockage principal
5. Aucun secret dans le code source
6. Installation reproductible

## Packaging (Phase 13)

| Élément | Emplacement |
| --- | --- |
| CI backend / frontend / E2E | `.github/workflows/` |
| Install + healthcheck | `scripts/install.*`, `scripts/healthcheck.*` |
| Backup / restore | `scripts/backup.*`, `scripts/restore.*` |
| Archive release | `scripts/package-release.*` → `dist/` |
| Documentation | [docs/installation.md](docs/installation.md), [configuration.md](docs/configuration.md), [architecture.md](docs/architecture.md), [security.md](docs/security.md), [troubleshooting.md](docs/troubleshooting.md), [workflows.md](docs/workflows.md) |

## Modules MVP

M01–M06, frontend MVP, E2E Playwright (Phase 12) et packaging (Phase 13) sont en place.

## Licence

MIT — voir `LICENSE`.
