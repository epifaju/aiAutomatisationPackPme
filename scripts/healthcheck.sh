#!/usr/bin/env bash
# Healthcheck des services exposés (ports hôte depuis .env).
# Production (P0.5) : ./scripts/healthcheck.sh --prod
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

compose_prod() {
  docker compose -f docker-compose.yml -f docker-compose.prod.yml "$@"
}

check_health() {
  local name="$1" container="$2"
  local status
  status="$(docker inspect --format '{{if .State.Health}}{{.State.Health.Status}}{{else}}{{.State.Status}}{{end}}' "$container" 2>/dev/null || echo missing)"
  if [[ "$status" == "healthy" ]]; then
    echo "OK    $name (healthy)"
  else
    echo "FAIL  $name ($container status=$status)"
    fail=1
  fi
}

check_exec() {
  local name="$1" service="$2" expect="$3"
  shift 3
  local out
  if out="$(compose_prod exec -T "$service" "$@" 2>/dev/null)"; then
    if [[ -n "$expect" ]] && ! echo "$out" | grep -q "$expect"; then
      echo "FAIL  $name (compose exec $service) — réponse inattendue"
      fail=1
    else
      echo "OK    $name"
    fi
  else
    echo "FAIL  $name (compose exec $service)"
    fail=1
  fi
}

if [[ "${1:-}" == "--prod" ]]; then
  echo "Healthcheck (overlay prod — Docker health + Caddy interne)"
  check_health "postgres" "aipack-postgres"
  check_health "redis" "aipack-redis"
  check_health "minio" "aipack-minio"
  check_health "ollama" "aipack-ollama"
  check_health "n8n" "aipack-n8n"
  check_health "mailpit" "aipack-mailpit"
  check_health "clamav" "aipack-clamav"
  check_health "backend" "aipack-backend"
  check_health "frontend" "aipack-frontend"
  check_health "caddy" "aipack-caddy"
  check_exec "caddy-readyz" "reverse-proxy" "ok" wget -qO- http://127.0.0.1:9080/readyz
  check_exec "backend-actuator" "backend" "UP" curl -fsS http://127.0.0.1:8080/actuator/health
  PROXY_SITE="$(load_env PROXY_SITE localhost)"
  host_args=()
  if [[ -n "$PROXY_SITE" && "$PROXY_SITE" != ":80" ]]; then
    host_args=(-H "Host: ${PROXY_SITE}")
  fi
  if curl -fsS --max-time 10 "${host_args[@]}" http://127.0.0.1/healthz >/dev/null 2>&1; then
    echo "OK    caddy-host (:80)"
  else
    echo "WARN  caddy-host (:80) — redirection TLS ou port 80 pas encore prêt"
  fi
else
  FRONTEND_PORT="$(load_env FRONTEND_PORT 5173)"
  BACKEND_PORT="$(load_env BACKEND_PORT 8080)"
  N8N_PORT="$(load_env N8N_PORT 5678)"
  MAILPIT_UI_PORT="$(load_env MAILPIT_UI_PORT 8025)"
  OLLAMA_PORT="$(load_env OLLAMA_PORT 11434)"
  MINIO_API_PORT="$(load_env MINIO_API_PORT 9000)"

  echo "Healthcheck (hôte)"
  check "frontend" "http://127.0.0.1:${FRONTEND_PORT}/healthz" "ok"
  check "backend"  "http://127.0.0.1:${BACKEND_PORT}/actuator/health" "UP"
  check "n8n"      "http://127.0.0.1:${N8N_PORT}/healthz"
  check "mailpit"  "http://127.0.0.1:${MAILPIT_UI_PORT}/api/v1/info"
  check "ollama"   "http://127.0.0.1:${OLLAMA_PORT}/api/tags"
  check "minio"    "http://127.0.0.1:${MINIO_API_PORT}/minio/health/live"
fi

if [[ "$fail" -ne 0 ]]; then
  echo "Un ou plusieurs contrôles ont échoué." >&2
  exit 1
fi
echo "Tous les contrôles passent."
