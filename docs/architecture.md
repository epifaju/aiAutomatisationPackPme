# Architecture

## Vue d’ensemble

Pack self-hosted pour TPE/PME : orchestration Docker Compose, automatisations n8n, IA locale Ollama, API Spring Boot, UI React, PostgreSQL (+ pgvector), MinIO, Mailpit, Redis.

```text
Navigateur → [Caddy optionnel] → frontend (Nginx) → /api → backend (Spring Boot)
                                    ↘ PostgreSQL (aipack)
    n8n ←→ PostgreSQL (n8n) + webhooks backend
backend ←→ Ollama (IA) · MinIO (documents + PJ email) · Redis (cache) · SMTP (Mailpit)
```

Entrée unique : profile Compose `proxy` en local ; overlay `docker-compose.prod.yml` en production (ports **80/443** seulement) — [proxy.md](proxy.md).
## Services Compose

| Service | Rôle |
| --- | --- |
| `postgres` | Bases `aipack` (métier) et `n8n` |
| `backend` | API REST, JWT, modules M01–M06, Flyway |
| `frontend` | SPA React + reverse proxy `/api` |
| `n8n` | Workflows (auth owner + BLOCK_ENV) |
| `n8n-init` | Owner bootstrap + credential webhook + import JSON |
| `ollama` / `ollama-init` | Inférence locale + pull modèle |
| `minio` / `minio-init` | Stockage objet documents + pièces jointes email |
| `backend` | API Spring Boot ; OCR Tesseract (fra+eng) pour scans PDF/PNG/JPEG |
| `clamav` | Antivirus clamd (profile `clamav` en local ; toujours on en overlay prod, P1.4) |
| `mailpit` | SMTP de test |
| `redis` | Cache AI authentifié (`REDIS_PASSWORD`, fail-open) |
| `reverse-proxy` | Caddy — profile `proxy` en local ; toujours actif avec `docker-compose.prod.yml` |

## Backend

- Java 21 / Spring Boot 3.5
- Sécurité JWT (access 15 min en mémoire ; refresh 7 j rotatif en cookie HttpOnly, P1.3)
- Modules : auth, audit, leads, emails, documents, invoices, reports, settings, automations
- Fournisseur IA abstrait (`AIProvider`) → Ollama (défaut), OpenAI-compatible (`openai`), ou Anthropic Messages (`anthropic`)
- Logs JSON structurés (Logback)

## Frontend

- React 19 + Vite + TypeScript + Tailwind
- Pages : Login, Dashboard, Inbox, Leads, Documents, Invoices, Automations, Audit, Settings
- Aucune clé API ni refresh JWT côté navigateur (cookie HttpOnly)

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
