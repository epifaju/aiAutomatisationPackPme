# AI Automation Pack for TPE/PME

Pack d’automatisation IA **self-hosted** pour TPE/PME françaises : n8n, IA locale (Ollama) ou API externe, emails, prospects, documents, factures, rapports et audit — installable via Docker Compose.

**Statut actuel : Phase 4 — Authentication.** JWT + refresh rotatif, login / refresh / logout / me. Aucun module métier. Le frontend n’est pas encore implémenté.

Le PRD `PRD___AI_Automation_Pack_for_TPE-PME_v1.1.md` est la source de vérité du projet.

## Objectif d’installation (cible MVP)

```bash
docker compose up -d
```

Cette commande démarre l’infrastructure, le backend et applique les migrations Flyway + le seed. Le frontend reste exclu (profile Compose `app`) jusqu’à la Phase 11.

## Stack imposée (PRD §5)

| Couche | Choix | Image pinée |
| --- | --- | --- |
| Orchestration | Docker Compose | — |
| Automatisation | n8n (PostgreSQL) | `n8nio/n8n:1.107.4` |
| Base de données | PostgreSQL 16 + pgvector | `pgvector/pgvector:0.8.6-pg16` |
| IA locale | Ollama | `ollama/ollama:0.11.10` |
| Backend | Spring Boot 3.5.5 / Java 21 / Maven | build local `backend/Dockerfile` |
| Frontend | React / TypeScript / Vite | Phase 11 |
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

3. Démarrer l’infrastructure et le backend :

```bash
docker compose up -d
```

Attendre les healthchecks (sans le one-shot MinIO) :

```bash
docker compose up -d postgres n8n ollama mailpit minio redis backend --wait
docker compose up minio-init
```

4. Contrôler :

```bash
docker compose ps
```

Le frontend reste derrière `--profile app` (Phase 11).

Compiler et tester le backend (Java 21) :

```bash
mvn -f backend/pom.xml verify
```

## Ports par défaut (hôte)

| Service | Port hôte | URL / usage |
| --- | --- | --- |
| PostgreSQL | `POSTGRES_PORT` (5432) | `localhost:5432` — les conteneurs parlent à `postgres:5432` |
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
- Backend : `GET /actuator/health` → `UP` (Flyway `V1`–`V4` + seed `R__demo_data`)
- n8n : `GET /healthz`
- Mailpit : `GET /api/v1/info` + SMTP
- Ollama : `GET /api/tags` (aucun modèle téléchargé)
- MinIO : `GET /minio/health/live` + bucket `aipack`
- Redis : `PING`

OpenAPI : `GET /v3/api-docs` · Swagger UI : `/swagger-ui.html`.

Aucun modèle Ollama n’est tiré automatiquement.

## Services Compose

| Service | Rôle | Profile | Phase |
| --- | --- | --- | --- |
| `postgres` | PostgreSQL + pgvector | défaut | 1 |
| `n8n` | Automatisation | défaut | 1 |
| `ollama` | IA locale | défaut | 1 |
| `mailpit` | SMTP / UI mails de test | défaut | 1 |
| `minio` | Stockage objet S3 | défaut | 1 |
| `minio-init` | Création du bucket (one-shot) | défaut | 1 |
| `redis` | Cache / files légères | défaut | 1 |
| `backend` | API Spring Boot | défaut | 2 |
| `frontend` | Dashboard React | `app` | 11 |

Le reverse proxy (Traefik/Caddy) n’est pas activé. Redis n’est une dépendance d’aucun autre service : le reste du stack démarre même si Redis est arrêté.

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
├── .github/workflows/          # CI — Phase 13
├── backend/
│   ├── pom.xml
│   ├── Dockerfile
│   └── src/                    # Spring Boot — Phase 2
├── frontend/src/               # React — Phase 11
├── database/
│   ├── init/                   # init Postgres (pgvector, base n8n)
│   ├── migrations/             # Flyway V1–V4 (baseline, identity, domain, refresh tokens)
│   └── seed/                   # R__demo_data.sql
├── n8n/
│   ├── workflows/
│   ├── credentials/            # gitignoré
│   └── README.md
├── ollama/
│   ├── models/                 # gitignoré (poids)
│   └── prompts/
├── scripts/                    # install / backup — Phase 13
├── docs/                       # documentation — Phase 13
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

`GET /`, health, OpenAPI et Swagger restent publics. Le reste de l’API exige un JWT. La racine redirige vers `/swagger-ui.html` (pas de frontend avant la Phase 11).

## Principes (PRD §4)

1. Self-hosted par défaut
2. Architecture Docker
3. Configuration par variables d’environnement
4. PostgreSQL comme stockage principal
5. Aucun secret dans le code source
6. Installation reproductible

## Modules MVP (non implémentés)

M01 Email Assistant · M02 Lead Management · M03 Document AI · M04 Invoice Reminder · M05 Daily Business Report · M06 Audit & Monitoring

## Licence

MIT — voir `LICENSE`.
