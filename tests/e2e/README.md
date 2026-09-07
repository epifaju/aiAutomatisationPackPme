# Tests E2E (Phase 12 — Playwright)

Scénarios PRD §40 sur la **stack réelle** (frontend + backend + Ollama + Mailpit + MinIO).

## Prérequis

```bash
docker compose up -d --wait
docker compose up minio-init ollama-init n8n-init
docker compose restart n8n
```

Frontend accessible (`APP_BASE_URL`, défaut `http://localhost:5173`) — Vite (`npm run dev`) ou conteneur `frontend`.

Variables lues depuis le `.env` racine : `BACKEND_PORT`, `FRONTEND_PORT`, `MAILPIT_UI_PORT`, `WEBHOOK_SECRET`, `DEMO_COMPANY_ID`, `OLLAMA_MODEL`, `E2E_AI_TIMEOUT_MS`.

Recommandé CPU : `OLLAMA_MODEL=llama3.2:1b`, `AI_TIMEOUT=600s` (ou plus).

## Lancer

```bash
cd tests/e2e
npm install
npx playwright install chromium
npm test
```

Sous PowerShell avec ports décalés, rien à exporter si le `.env` racine est à jour.

Le `global-setup` vérifie le backend, la présence du modèle Ollama, puis **warm** le modèle.

### Mode strict IA

| Contexte | Comportement scénario B |
| --- | --- |
| Local (défaut) | **Skip** si Ollama timeout / pas de `PENDING_APPROVAL` |
| `CI=true` ou `E2E_STRICT_AI=1` | **Échec** (pas de skip) |

CI GitHub force `E2E_STRICT_AI=1` + `llama3.2:1b` + warm explicite.

Si le frontend Vite loggue des `ECONNRESET` vers le backend, redémarrer Vite (`cd frontend && npm run dev`) ou utiliser le conteneur `frontend`.

## Scénarios

| Spec | PRD | Parcours |
| --- | --- | --- |
| `a-lead.spec.ts` | A | Webhook lead + qualify → UI Leads + audit |
| `b-email.spec.ts` | B | Webhook email + analyze → Inbox Approuver → Envoyer → Mailpit + audit |
| `c-invoice.spec.ts` | C | Facture en retard → détecter → Approuver/Envoyer → Mailpit + audit |
| `d-document.spec.ts` | D | Upload document → extraction → (Approuver si revue) |
