# Architecture

## Vue d’ensemble

Pack self-hosted pour TPE/PME : orchestration Docker Compose, automatisations n8n, IA locale Ollama, API Spring Boot, UI React, PostgreSQL (+ pgvector), MinIO, Mailpit, Redis.

```text
Navigateur → frontend (Nginx) → /api → backend (Spring Boot)
                                    ↘ PostgreSQL (aipack)
n8n ←→ PostgreSQL (n8n) + webhooks backend
backend ←→ Ollama (IA) · MinIO (fichiers) · Redis (cache) · SMTP (Mailpit)
```

## Services Compose

| Service | Rôle |
| --- | --- |
| `postgres` | Bases `aipack` (métier) et `n8n` |
| `backend` | API REST, JWT, modules M01–M06, Flyway |
| `frontend` | SPA React + reverse proxy `/api` |
| `n8n` | Workflows (emails, leads, docs, factures, rapports, audit) |
| `n8n-init` | Import one-shot des JSON `n8n/workflows/` |
| `ollama` / `ollama-init` | Inférence locale + pull modèle |
| `minio` / `minio-init` | Stockage objet documents |
| `mailpit` | SMTP de test |
| `redis` | Cache AI (fail-open) |

## Backend

- Java 21 / Spring Boot 3.5
- Sécurité JWT (access 15 min, refresh 7 j rotatif)
- Modules : auth, audit, leads, emails, documents, invoices, reports, settings, automations
- Fournisseur IA abstrait (`AIProvider`) → Ollama
- Logs JSON structurés (Logback)

## Frontend

- React 19 + Vite + TypeScript + Tailwind
- Pages : Login, Dashboard, Inbox, Leads, Documents, Invoices, Automations, Audit, Settings
- Aucune clé API côté navigateur

## Données

- Migrations Flyway `database/migrations/`
- Seed démo `database/seed/R__demo_data.sql`
- Workflows versionnés `n8n/workflows/` ; credentials n8n **non** versionnés

## CI

| Workflow | Contenu |
| --- | --- |
| `backend-ci.yml` | `mvn verify` |
| `frontend-ci.yml` | `npm ci` / lint / test / build |
| `e2e.yml` | Compose + Playwright |

Voir aussi le PRD §5–§8 et §40bis.
