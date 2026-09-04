#!/usr/bin/env bash
# Install AI Automation Pack — Linux / macOS / Git Bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

echo "==> AI Automation Pack — installation"

if ! command -v docker >/dev/null 2>&1; then
  echo "Docker est requis. Installez Docker Desktop ou Docker Engine." >&2
  exit 1
fi

if ! docker compose version >/dev/null 2>&1; then
  echo "Docker Compose v2 est requis (docker compose)." >&2
  exit 1
fi

if [[ ! -f .env ]]; then
  cp .env.example .env
  echo "Fichier .env créé depuis .env.example — changez les secrets avant production."
else
  echo ".env déjà présent — conservation."
fi

echo "==> Validation Compose"
docker compose config >/dev/null

echo "==> Démarrage des services (healthchecks)"
docker compose up -d postgres n8n ollama mailpit minio redis backend frontend --wait

echo "==> Initialisations one-shot (MinIO bucket, modèle Ollama, workflows n8n)"
docker compose up minio-init ollama-init n8n-init

echo "==> Redémarrage n8n (prise en compte des workflows importés)"
docker compose restart n8n
docker compose up -d n8n --wait

echo "==> Healthcheck"
bash "$ROOT/scripts/healthcheck.sh"

FRONTEND_PORT="$(grep -E '^FRONTEND_PORT=' .env | cut -d= -f2- || true)"
FRONTEND_PORT="${FRONTEND_PORT:-5173}"

cat <<EOF

Installation terminée.

  UI          : http://localhost:${FRONTEND_PORT}
  Compte démo : demo.admin@aipack.example / DemoAdmin!2026
  Docs        : docs/installation.md

EOF
