#!/usr/bin/env bash
# Healthcheck des services exposés (ports hôte depuis .env)
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

load_env() {
  local key="$1" default="$2"
  if [[ -f .env ]]; then
    local val
    val="$(grep -E "^${key}=" .env | tail -n1 | cut -d= -f2- || true)"
    if [[ -n "${val}" ]]; then
      echo "$val"
      return
    fi
  fi
  echo "$default"
}

FRONTEND_PORT="$(load_env FRONTEND_PORT 5173)"
BACKEND_PORT="$(load_env BACKEND_PORT 8080)"
N8N_PORT="$(load_env N8N_PORT 5678)"
MAILPIT_UI_PORT="$(load_env MAILPIT_UI_PORT 8025)"
OLLAMA_PORT="$(load_env OLLAMA_PORT 11434)"
MINIO_API_PORT="$(load_env MINIO_API_PORT 9000)"

fail=0
check() {
  local name="$1" url="$2" expect="${3:-}"
  if out="$(curl -fsS --max-time 10 "$url" 2>/dev/null)"; then
    if [[ -n "$expect" ]] && ! echo "$out" | grep -q "$expect"; then
      echo "FAIL  $name ($url) — réponse inattendue"
      fail=1
    else
      echo "OK    $name"
    fi
  else
    echo "FAIL  $name ($url)"
    fail=1
  fi
}

echo "Healthcheck (hôte)"
check "frontend" "http://127.0.0.1:${FRONTEND_PORT}/healthz" "ok"
check "backend"  "http://127.0.0.1:${BACKEND_PORT}/actuator/health" "UP"
check "n8n"      "http://127.0.0.1:${N8N_PORT}/healthz"
check "mailpit"  "http://127.0.0.1:${MAILPIT_UI_PORT}/api/v1/info"
check "ollama"   "http://127.0.0.1:${OLLAMA_PORT}/api/tags"
check "minio"    "http://127.0.0.1:${MINIO_API_PORT}/minio/health/live"

if [[ "$fail" -ne 0 ]]; then
  echo "Un ou plusieurs contrôles ont échoué." >&2
  exit 1
fi
echo "Tous les contrôles passent."
