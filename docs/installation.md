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

## Accès

| Service | URL par défaut |
| --- | --- |
| UI | http://localhost:5173 |
| Backend / Swagger | http://localhost:8080/swagger-ui.html |
| n8n | http://localhost:5678 |
| Mailpit | http://localhost:8025 |

Compte démo (développement uniquement) :

```text
demo.admin@aipack.example / DemoAdmin!2026
```

## Collision de ports

Modifiez les variables `*_PORT` dans `.env`, puis relancez Compose. Exemple : `BACKEND_PORT=18080`, `N8N_PORT=5677`, `POSTGRES_PORT=5437`.

## Vérification

```powershell
.\scripts\healthcheck.ps1
```

```bash
./scripts/healthcheck.sh
```

Smoke n8n + Ollama : `scripts/smoke-n8n-ollama.ps1`.

## Suite

- Configuration : [configuration.md](configuration.md)
- Sauvegarde : [configuration.md](configuration.md#sauvegarde--restauration) et `scripts/backup.*`
- Dépannage : [troubleshooting.md](troubleshooting.md)
