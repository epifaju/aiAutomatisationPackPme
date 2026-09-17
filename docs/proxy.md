# Reverse proxy (Caddy)

Point d’entrée unique vers l’UI, `/api` et les webhooks publics.

| Mode | Fichiers Compose | Ports hôte | Services internes |
| --- | --- | --- | --- |
| Développement (défaut) | `docker-compose.yml` | UI/API/n8n/Mailpit/… publiés | Accès direct pour démo et E2E |
| Proxy local (optionnel) | + `--profile proxy` | `8088` / `8443` **en plus** | Inchangé |
| **Production (P0.5)** | `docker-compose.yml` + `docker-compose.prod.yml` | **80 et 443 uniquement** | Postgres, Redis, MinIO, Ollama, n8n, Mailpit, backend, frontend, ClamAV **non publiés** |

## Production (TLS, surface minimale)

1. DNS A/AAAA vers le serveur.
2. Dans `.env` (secrets réels, pas les placeholders) :

```env
APP_ENV=production
SPRING_PROFILES_ACTIVE=prod
APP_BASE_URL=https://app.exemple.com
PROXY_SITE=app.exemple.com
PROXY_ACME_EMAIL=admin@exemple.com
RATE_LIMIT_TRUST_FORWARDED_HEADERS=true
```

3. Démarrer :

```bash
docker compose -f docker-compose.yml -f docker-compose.prod.yml config
docker compose -f docker-compose.yml -f docker-compose.prod.yml up -d --wait
docker compose -f docker-compose.yml -f docker-compose.prod.yml up minio-init ollama-init n8n-init
docker compose -f docker-compose.yml -f docker-compose.prod.yml restart n8n
```

```powershell
docker compose -f docker-compose.yml -f docker-compose.prod.yml up -d --wait
docker compose -f docker-compose.yml -f docker-compose.prod.yml up minio-init ollama-init n8n-init
docker compose -f docker-compose.yml -f docker-compose.prod.yml restart n8n
.\scripts\healthcheck.ps1 -Prod
```

Let’s Encrypt est géré par Caddy (`PROXY_SITE` = hostname + `PROXY_ACME_EMAIL`). Les ports hôte **80** et **443** (TCP + UDP 443 pour HTTP/3) doivent être libres et ouverts. ClamAV démarre avec l’overlay (signatures au premier boot, souvent 1–3 min) — [security.md](security.md).

Vérifier l’UI : `https://app.exemple.com/healthz` → `ok`.

Les webhooks publics : `https://app.exemple.com/webhook/...` (header `X-Webhook-Secret` **de l’entreprise**, pas un secret unique pour tout le serveur).

### Accès n8n / consoles (non publiés)

n8n, MinIO console, Mailpit, Postgres, Redis, Ollama **n’écoutent plus l’hôte**. Pour l’UI n8n depuis un poste admin :

```bash
# IP du conteneur n8n, puis tunnel SSH
docker inspect -f '{{range.NetworkSettings.Networks}}{{.IPAddress}}{{end}}' aipack-n8n
ssh -N -L 5678:<ip-conteneur>:5678 user@serveur
```

Puis http://127.0.0.1:5678 (compte `N8N_OWNER_*`).

Sous-domaine n8n (optionnel) : voir `proxy/Caddyfile.n8n.example` — ce n’est **pas** le défaut P0.5.

## Proxy local (développement)

Les ports internes restent publiés. Caddy s’ajoute sur `PROXY_HTTP_PORT` (8088) :

```bash
docker compose --profile proxy up -d reverse-proxy --wait
curl -s http://127.0.0.1:8088/healthz
```

```powershell
docker compose --profile proxy up -d reverse-proxy --wait
```

| Variable | Défaut | Rôle |
| --- | --- | --- |
| `PROXY_HTTP_PORT` | `8088` | Port hôte HTTP **local** (ignoré en overlay prod → 80) |
| `PROXY_HTTPS_PORT` | `8443` | Port hôte HTTPS **local** (ignoré en overlay prod → 443) |
| `PROXY_SITE` | `:80` | Site Caddy — `:80` en local, hostname en prod TLS |
| `PROXY_ACME_EMAIL` | vide | Email Let’s Encrypt (prod) |

## Fichiers

- `docker-compose.prod.yml` — retire les `ports:` internes ; Caddy toujours démarré sur 80/443
- `proxy/Caddyfile` — config montée dans le conteneur (`/readyz` interne sur `:9080`)
- `proxy/Caddyfile.n8n.example` — sous-domaine n8n optionnel

## Limites

- Un replica backend : rate limiting Bucket4j in-memory (OK derrière un seul Caddy). En prod, `RATE_LIMIT_TRUST_FORWARDED_HEADERS` est `true` par défaut dans l’overlay.
- Pas de Traefik : Caddy couvre TLS + routing TPE/PME self-hosted.
