#!/usr/bin/env bash
# Crée une archive source « release » sans secrets, node_modules ni volumes.
# Usage: ./scripts/package-release.sh [version]
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

VERSION="${1:-0.1.0}"
STAMP="$(date -u +%Y%m%d)"
OUT_DIR="$ROOT/dist"
NAME="aipack-${VERSION}-${STAMP}"
STAGE="$OUT_DIR/$NAME"
ARCHIVE="$OUT_DIR/${NAME}.zip"

rm -rf "$STAGE"
mkdir -p "$STAGE"

copy_tree() {
  # Portable: tar pipe (pas de dépendance rsync)
  tar -C "$ROOT" \
    --exclude='.git' \
    --exclude='.env' \
    --exclude='./.env.*' \
    --exclude='backups' \
    --exclude='dist' \
    --exclude='data' \
    --exclude='backend/target' \
    --exclude='frontend/node_modules' \
    --exclude='frontend/dist' \
    --exclude='tests/e2e/node_modules' \
    --exclude='tests/e2e/test-results' \
    --exclude='tests/e2e/playwright-report' \
    --exclude='n8n/credentials/*' \
    --exclude='ollama/models/*' \
    --exclude='*.log' \
    -cf - . | tar -C "$STAGE" -xf -
}

copy_tree

cp -f "$ROOT/.env.example" "$STAGE/.env.example"
# Retirer tout .env.* accidentel sauf .env.example
find "$STAGE" -maxdepth 1 -name '.env.*' ! -name '.env.example' -delete 2>/dev/null || true
mkdir -p "$STAGE/n8n/credentials" "$STAGE/ollama/models"
touch "$STAGE/n8n/credentials/.gitkeep" "$STAGE/ollama/models/.gitkeep"

(
  cd "$OUT_DIR"
  if command -v zip >/dev/null 2>&1; then
    rm -f "$(basename "$ARCHIVE")"
    zip -rq "$(basename "$ARCHIVE")" "$NAME"
  else
    tar czf "${NAME}.tar.gz" "$NAME"
    ARCHIVE="$OUT_DIR/${NAME}.tar.gz"
  fi
)

echo "Release package : $ARCHIVE"
echo "Contenu : sources + workflows + docs + scripts (sans secrets)."
