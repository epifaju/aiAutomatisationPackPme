#!/usr/bin/env bash
# Restore depuis une archive produite par backup.sh
# Usage: ./scripts/restore.sh <répertoire_backup>
# Restaure PostgreSQL (aipack + n8n), volumes n8n_data et minio_data.
# N’écrase pas .env sauf si RESTORE_ENV=1 et env.secrets présent.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

SRC="${1:-}"
if [[ -z "$SRC" || ! -d "$SRC" ]]; then
  echo "Usage: $0 <répertoire_backup>" >&2
  exit 1
fi

POSTGRES_USER="$(grep -E '^POSTGRES_USER=' .env 2>/dev/null | cut -d= -f2- || echo aipack)"
POSTGRES_DB="$(grep -E '^POSTGRES_DB=' .env 2>/dev/null | cut -d= -f2- || echo aipack)"
N8N_DB="$(grep -E '^N8N_DB=' .env 2>/dev/null | cut -d= -f2- || echo n8n)"
PROJECT="$(docker compose config --format json 2>/dev/null | sed -n 's/.*"name"[[:space:]]*:[[:space:]]*"\([^"]*\)".*/\1/p' | head -n1)"
PROJECT="${PROJECT:-ai-automation-pack}"

restore_volume() {
  local name="$1"
  local archive="$2"
  local service="$3"
  local vol="${PROJECT}_${name}"
  if [[ ! -f "$SRC/$archive" ]]; then
    echo "→ Skip ${name} (pas de $archive)"
    return
  fi
  echo "→ Restore volume ${name}"
  docker compose stop "$service" 2>/dev/null || true
  docker volume create "$vol" >/dev/null 2>&1 || true
  docker run --rm -v "${vol}:/data" -v "$(cd "$SRC" && pwd):/backup:ro" alpine:3.20 \
    sh -c "rm -rf /data/* /data/.[!.]* 2>/dev/null; tar xzf /backup/${archive} -C /data"
}

echo "==> Restore depuis $SRC"
echo "Les bases $POSTGRES_DB / $N8N_DB et volumes n8n/minio seront écrasés. Ctrl+C pour annuler (5 s)."
sleep 5

docker compose up -d postgres --wait

if [[ -f "$SRC/postgres-aipack.dump" ]]; then
  echo "→ Restore $POSTGRES_DB"
  docker compose exec -T postgres dropdb -U "$POSTGRES_USER" --if-exists "$POSTGRES_DB"
  docker compose exec -T postgres createdb -U "$POSTGRES_USER" "$POSTGRES_DB"
  docker compose exec -T postgres pg_restore -U "$POSTGRES_USER" -d "$POSTGRES_DB" --no-owner \
    < "$SRC/postgres-aipack.dump" || true
fi

if [[ -f "$SRC/postgres-n8n.dump" ]]; then
  echo "→ Restore $N8N_DB"
  docker compose exec -T postgres dropdb -U "$POSTGRES_USER" --if-exists "$N8N_DB"
  docker compose exec -T postgres createdb -U "$POSTGRES_USER" "$N8N_DB"
  docker compose exec -T postgres pg_restore -U "$POSTGRES_USER" -d "$N8N_DB" --no-owner \
    < "$SRC/postgres-n8n.dump" || true
fi

restore_volume "n8n_data" "n8n_data.tar.gz" "n8n"
restore_volume "minio_data" "minio_data.tar.gz" "minio"

if [[ "${RESTORE_ENV:-0}" == "1" && -f "$SRC/env.secrets" ]]; then
  cp "$SRC/env.secrets" .env
  echo "→ .env restauré depuis env.secrets"
fi

echo "→ Redémarrage stack"
docker compose up -d postgres n8n ollama mailpit minio redis backend frontend --wait
docker compose up minio-init || true
docker compose restart n8n || true

echo "Restore terminé. Vérifiez avec scripts/healthcheck.sh"
