# Dépannage

## `docker compose up` échoue / port déjà utilisé

Symptôme : `Bind for 0.0.0.0:xxxx failed`.

Action : modifier le port hôte dans `.env` (`POSTGRES_PORT`, `BACKEND_PORT`, `N8N_PORT`, `MAILPIT_UI_PORT`, …) puis `docker compose up -d` à nouveau. Aligner `N8N_WEBHOOK_URL` avec `N8N_PORT`.

## Frontend OK mais API en erreur

- Vérifier `docker compose ps` : `backend` healthy.
- Depuis l’hôte : `http://localhost:$BACKEND_PORT/actuator/health`.
- Via UI : `/api/` est proxifié par Nginx ; en Vite local, vérifier `API_PROXY_TARGET`.

## Ollama lent / timeout IA

- Sur CPU, préférer `OLLAMA_MODEL=llama3.2:1b` (mesuré ~30–60 s pour un Qualifier à chaud).
- `llama3.2` (~3B) peut dépasser 15 min et finir en `IA indisponible` malgré `AI_TIMEOUT=900s`.
- `OLLAMA_KEEP_ALIVE=-1` évite de décharger le modèle ; warm : `ollama run <model> "OK"`.
- Ne pas laisser deux modèles chargés en parallèle (bloquent le CPU).
- Relancer : `docker compose up ollama-init` puis `docker compose up -d --force-recreate backend`.
- Smoke : `.\scripts\smoke-n8n-ollama.ps1`.
- Alternative cloud : `AI_PROVIDER=openai` + `AI_OPENAI_API_KEY` (voir ci-dessous).

## IA externe (OpenAI-compatible)

```env
AI_PROVIDER=openai
AI_OPENAI_API_KEY=sk-...
AI_OPENAI_MODEL=gpt-4o-mini
# AI_OPENAI_BASE_URL=https://api.mistral.ai/v1
```

Sans clé API, le backend refuse de démarrer. Anthropic natif non branché ; un proxy compatible OpenAI peut servir via `AI_OPENAI_BASE_URL`.

## Document en statut `AI_UNAVAILABLE` / `ERROR`

Ollama / OpenAI indisponible ou timeout. Vérifier le provider (`AI_PROVIDER`), les logs `backend`, et pour Ollama `GET /api/tags`.

## Triangle rouge sur workflow Error Handler (WF091)

Normal : le nœud **Error Trigger** n’est pas activable. Le workflow doit rester **inactif**. Les autres workflows `[AIPACK]*` doivent être actifs après `n8n-init` + restart n8n. WF091 écrit tout de même dans l’audit backend (`POST /webhook/audit/n8n-error`) quand un autre workflow échoue.

## n8n workflows manquants

```bash
docker compose up n8n-init
docker compose restart n8n
```

Voir `n8n/README.md`.

## Login démo échoue

Compte : `demo.admin@aipack.example` / `DemoAdmin!2026` (profils dev/test). Vérifier que Flyway a appliqué le seed et que le backend a démarré (réécriture BCrypt).

## Healthcheck échoue

```powershell
.\scripts\healthcheck.ps1
docker compose logs --tail=100 backend frontend n8n
```

## Tests E2E flaky

Sur CPU, le scénario email (IA) peut skip/timeout. Les scénarios lead / facture / document restent utilisables. Détails : `tests/e2e/README.md`.

## Backup / restore

Si `pg_restore` affiche des warnings : souvent non bloquants. Vérifier ensuite le healthcheck et un login. Volume n8n : le nom dépend de `COMPOSE_PROJECT_NAME` / `name:` Compose (`ai-automation-pack_n8n_data` par défaut).
