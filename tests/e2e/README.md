# Tests E2E (Phase 12 — Playwright)

Scénarios PRD §40 sur la **stack réelle** (frontend + backend + Ollama + Mailpit + MinIO).

## Prérequis

```bash
docker compose up -d --wait
docker compose up minio-init ollama-init n8n-init
docker compose restart n8n
```

Frontend accessible (`APP_BASE_URL`, défaut `http://localhost:5173`) — Vite (`npm run dev`) ou conteneur `frontend`.

Variables lues depuis le `.env` racine : `BACKEND_PORT`, `FRONTEND_PORT`, `MAILPIT_UI_PORT`, `WEBHOOK_SECRET`, `DEMO_COMPANY_ID`.

## Lancer

```bash
cd tests/e2e
npm install
npx playwright install chromium
npm test
```

Sous PowerShell avec ports décalés, rien à exporter si le `.env` racine est à jour.

Timeout IA : `E2E_AI_TIMEOUT_MS` (défaut 420000). Sur CPU, recommander `AI_TIMEOUT=300s` dans `.env` puis `docker compose up -d backend --force-recreate`.

Le scénario B (email, 2 appels LLM) est **skippé** si Ollama est trop lent/indisponible — A/C/D restent bloquants.

Si le frontend Vite loggue des `ECONNRESET` vers le backend, redémarrer Vite (`cd frontend && npm run dev`) ou utiliser le conteneur `frontend`.

## Scénarios

| Spec | PRD | Parcours |
| --- | --- | --- |
| `a-lead.spec.ts` | A | Webhook lead + qualify → UI Leads + audit |
| `b-email.spec.ts` | B | Webhook email + analyze → Inbox Approuver + audit |
| `c-invoice.spec.ts` | C | Facture en retard → détecter → Approuver/Envoyer → Mailpit + audit |
| `d-document.spec.ts` | D | Upload facture texte → statut extraction + audit |

Rapport HTML : `npx playwright show-report`.
