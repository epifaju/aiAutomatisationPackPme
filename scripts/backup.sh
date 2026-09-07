#!/usr/bin/env bash
# Backup PostgreSQL (aipack + n8n) + volumes n8n_data + minio_data + config non secrète
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

backup_volume() {
  local name="$1"
  local archive="$2"
  local vol="${PROJECT}_${name}"
  echo "→ Volume ${name}"
  if docker volume inspect "$vol" >/dev/null 2>&1; then
    docker run --rm -v "${vol}:/data:ro" -v "$OUT:/backup" alpine:3.20 \
      tar czf "/backup/${archive}" -C /data .
    echo "includes_${name}=true" >> "$MANIFEST"
  else
    echo "Volume $vol introuvable — skip ${name}" >&2
    echo "includes_${name}=false" >> "$MANIFEST"
  fi
}

echo "==> Backup vers $OUT"

echo "→ PostgreSQL ($POSTGRES_DB)"
docker compose exec -T postgres pg_dump -U "$POSTGRES_USER" -d "$POSTGRES_DB" --no-owner --format=custom \
  > "$OUT/postgres-aipack.dump"

echo "→ PostgreSQL ($N8N_DB)"
docker compose exec -T postgres pg_dump -U "$POSTGRES_USER" -d "$N8N_DB" --no-owner --format=custom \
  > "$OUT/postgres-n8n.dump"

MANIFEST="$OUT/MANIFEST.txt"
{
  echo "created_at_utc=$STAMP"
  echo "project=$PROJECT"
  echo "postgres_db=$POSTGRES_DB"
  echo "n8n_db=$N8N_DB"
  echo "includes_env=false"
} > "$MANIFEST"

backup_volume "n8n_data" "n8n_data.tar.gz"

# Pause MinIO briefly for a consistent object-store snapshot.
echo "→ Pause MinIO for consistent snapshot"
docker compose stop minio >/dev/null 2>&1 || true
backup_volume "minio_data" "minio_data.tar.gz"
docker compose start minio >/dev/null 2>&1 || true
docker compose up -d minio --wait >/dev/null 2>&1 || true

echo "→ Configuration (sans secrets)"
cp .env.example "$OUT/env.example"
cp docker-compose.yml "$OUT/docker-compose.yml"
if [[ -f docker-compose.dev.yml ]]; then
  cp docker-compose.dev.yml "$OUT/docker-compose.dev.yml"
fi
if [[ -d proxy ]]; then
  cp -a proxy "$OUT/proxy"
fi

if [[ "${INCLUDE_ENV:-0}" == "1" ]]; then
  if [[ -f .env ]]; then
    cp .env "$OUT/env.secrets"
    # rewrite includes_env in manifest
    if grep -q '^includes_env=' "$MANIFEST"; then
      sed -i.bak 's/^includes_env=.*/includes_env=true/' "$MANIFEST" && rm -f "$MANIFEST.bak"
    else
      echo "includes_env=true" >> "$MANIFEST"
    fi
    echo "ATTENTION : .env copié en clair dans $OUT/env.secrets — protégez cette archive." >&2
  fi
else
  echo "Note : .env non inclus (INCLUDE_ENV=1 pour l’ajouter). Voir docs/security.md."
fi

echo "$OUT" > "$OUT_BASE/latest.txt"
echo "Backup terminé : $OUT"
