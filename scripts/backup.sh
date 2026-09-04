#!/usr/bin/env bash
# Backup PostgreSQL (aipack + n8n) + volume n8n_data + config non secrète
# Usage: ./scripts/backup.sh [répertoire_sortie]
# Option: INCLUDE_ENV=1 pour copier .env (ATTENTION : secrets en clair)
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

STAMP="$(date -u +%Y%m%dT%H%M%SZ)"
OUT_BASE="${1:-$ROOT/backups}"
OUT="$OUT_BASE/aipack-backup-$STAMP"
mkdir -p "$OUT"

POSTGRES_USER="$(grep -E '^POSTGRES_USER=' .env 2>/dev/null | cut -d= -f2- || echo aipack)"
POSTGRES_DB="$(grep -E '^POSTGRES_DB=' .env 2>/dev/null | cut -d= -f2- || echo aipack)"
N8N_DB="$(grep -E '^N8N_DB=' .env 2>/dev/null | cut -d= -f2- || echo n8n)"
PROJECT="$(docker compose config --format json 2>/dev/null | sed -n 's/.*"name"[[:space:]]*:[[:space:]]*"\([^"]*\)".*/\1/p' | head -n1)"
PROJECT="${PROJECT:-ai-automation-pack}"

echo "==> Backup vers $OUT"

echo "→ PostgreSQL ($POSTGRES_DB)"
docker compose exec -T postgres pg_dump -U "$POSTGRES_USER" -d "$POSTGRES_DB" --no-owner --format=custom \
  > "$OUT/postgres-aipack.dump"

echo "→ PostgreSQL ($N8N_DB)"
docker compose exec -T postgres pg_dump -U "$POSTGRES_USER" -d "$N8N_DB" --no-owner --format=custom \
  > "$OUT/postgres-n8n.dump"

echo "→ Volume n8n_data"
VOL="${PROJECT}_n8n_data"
if docker volume inspect "$VOL" >/dev/null 2>&1; then
  docker run --rm -v "${VOL}:/data:ro" -v "$OUT:/backup" alpine:3.20 \
    tar czf /backup/n8n_data.tar.gz -C /data .
else
  echo "Volume $VOL introuvable — skip n8n_data" >&2
fi

echo "→ Configuration (sans secrets)"
cp .env.example "$OUT/env.example"
cp docker-compose.yml "$OUT/docker-compose.yml"
if [[ -f docker-compose.dev.yml ]]; then
  cp docker-compose.dev.yml "$OUT/docker-compose.dev.yml"
fi

MANIFEST="$OUT/MANIFEST.txt"
{
  echo "created_at_utc=$STAMP"
  echo "project=$PROJECT"
  echo "postgres_db=$POSTGRES_DB"
  echo "n8n_db=$N8N_DB"
  echo "includes_env=false"
} > "$MANIFEST"

if [[ "${INCLUDE_ENV:-0}" == "1" ]]; then
  if [[ -f .env ]]; then
    cp .env "$OUT/env.secrets"
    echo "includes_env=true" >> "$MANIFEST"
    echo "ATTENTION : .env copié en clair dans $OUT/env.secrets — protégez cette archive." >&2
  fi
else
  echo "Note : .env non inclus (INCLUDE_ENV=1 pour l’ajouter). Voir docs/security.md."
fi

echo "$OUT" > "$OUT_BASE/latest.txt"
echo "Backup terminé : $OUT"
