# Reverse proxy (Caddy)

Config **esquissée** (PRD) : non activée par défaut. Point d’entrée unique HTTP(S) vers l’UI + API + webhooks publics.

## Activation (local)

```bash
docker compose --profile proxy up -d reverse-proxy --wait
```

```powershell
docker compose --profile proxy up -d reverse-proxy --wait
```

Par défaut : http://localhost:8088 (variable `PROXY_HTTP_PORT`).

Vérifier :

```bash
curl -s http://127.0.0.1:8088/healthz
```

L’UI et `/api/*` passent par le frontend Nginx ; `/webhook*` est routé directement vers le backend.

## Variables

| Variable | Défaut | Rôle |
| --- | --- | --- |
| `PROXY_HTTP_PORT` | `8088` | Port hôte HTTP |
| `PROXY_HTTPS_PORT` | `8443` | Port hôte HTTPS (utile si `PROXY_SITE` est un hostname) |
| `PROXY_SITE` | `:80` | Adresse Caddy (écoute conteneur) — `:80` en local, ou `app.exemple.com` pour TLS |

## Production (TLS)

1. DNS A/AAAA vers le serveur.
2. Dans `.env` :

```env
PROXY_SITE=app.exemple.com
PROXY_HTTP_PORT=80
PROXY_HTTPS_PORT=443
APP_BASE_URL=https://app.exemple.com
RATE_LIMIT_TRUST_FORWARDED_HEADERS=true
```

Ajouter en tête de `proxy/Caddyfile` : `{ email admin@exemple.com }` (Let’s Encrypt).

3. Démarrer avec `--profile proxy`.
4. **Ne pas** publier sur Internet : Mailpit, console MinIO, Postgres, Ollama. Idéalement retirer les `ports:` hôte de `frontend` / `backend` une fois le proxy stable (accès uniquement via Caddy).
5. n8n : soit port dédié firewallé, soit sous-domaine (voir `proxy/Caddyfile.n8n.example`).

## Fichiers

- `proxy/Caddyfile` — config active montée dans le conteneur
- `proxy/Caddyfile.n8n.example` — exemple sous-domaine n8n

## Limites MVP

- Un seul replica backend : le rate limiting Bucket4j reste in-memory (OK derrière un seul proxy).
- Pas de Traefik dans ce pack : Caddy couvre TLS + routing pour le scénario TPE/PME self-hosted.
