# Installation

## Prérequis

- Docker Desktop (Windows / macOS) ou Docker Engine + Compose v2 (Linux)
- ~8 Go RAM recommandés (Ollama + `llama3.2` sur CPU)
- Ports libres : voir `.env.example` (`FRONTEND_PORT`, `BACKEND_PORT`, `N8N_PORT`, etc.)

## Installation rapide

### Windows (PowerShell)

```powershell
.\scripts\install.ps1
```

### Linux / macOS

```bash
chmod +x scripts/*.sh
./scripts/install.sh
```

Le script :

1. copie `.env.example` → `.env` si besoin ;
2. valide `docker compose config` ;
3. démarre postgres, n8n, ollama, mailpit, minio, redis, backend, frontend ;
4. lance `minio-init`, `ollama-init` (télécharge le modèle IA), `n8n-init` (import workflows) ;
5. redémarre n8n ;
6. exécute le healthcheck.

## Installation manuelle

```bash
cp .env.example .env   # ou Copy-Item sous PowerShell
docker compose config
docker compose up -d postgres n8n ollama mailpit minio redis backend frontend --wait
docker compose up minio-init ollama-init n8n-init
docker compose restart n8n
```

## Production (P0.5 — Caddy uniquement)

Overlay `docker-compose.prod.yml` : **seuls les ports 80 et 443** sont publiés. Détail : [proxy.md](proxy.md).

```bash
docker compose -f docker-compose.yml -f docker-compose.prod.yml up -d --wait
docker compose -f docker-compose.yml -f docker-compose.prod.yml up minio-init ollama-init n8n-init
./scripts/healthcheck.sh --prod
```

## Accès

| Service | URL par défaut |
| --- | --- |
| UI (dev) | http://localhost:5173 |
| UI (prod) | `https://<PROXY_SITE>` — seuls 80/443 publiés |
| Backend / Swagger | http://localhost:8080/swagger-ui.html |
| n8n | http://localhost:5678 (login owner `N8N_OWNER_*`) |
| Mailpit | http://localhost:8025 |

Compte démo app (développement uniquement, seed hors prod) :

```text
demo.admin@aipack.example / DemoAdmin!2026
```

Compte n8n (défaut `.env.example`, à changer) :

```text
n8n.owner@aipack.example / change-me-n8n-owner-password
```

## Collision de ports

Modifiez les variables `*_PORT` dans `.env`, puis relancez Compose. Exemple : `BACKEND_PORT=18080`, `N8N_PORT=5677`, `POSTGRES_PORT=5437`.

## Vérification

```powershell
.\scripts\healthcheck.ps1
.\scripts\healthcheck.ps1 -Prod   # overlay production
```

```bash
./scripts/healthcheck.sh
./scripts/healthcheck.sh --prod
```

Smoke n8n + Ollama : `scripts/smoke-n8n-ollama.ps1`.

## Suite

- Configuration : [configuration.md](configuration.md)
- Reverse proxy production : [proxy.md](proxy.md)
- Sauvegarde : [configuration.md](configuration.md#sauvegarde--restauration) et `scripts/backup.*`
- Dépannage : [troubleshooting.md](troubleshooting.md)
