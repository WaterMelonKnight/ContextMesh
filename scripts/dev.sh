#!/usr/bin/env bash
set -Eeuo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
WEB_ORIGIN="${CONTEXTMESH_PUBLIC_WEB_ORIGIN:-http://localhost:3000}"
INTERNAL_API_ORIGIN="${CONTEXTMESH_INTERNAL_API_ORIGIN:-http://127.0.0.1:8080}"

validate_origin() {
  node -e '
    const value = process.argv[1];
    let url;
    try { url = new URL(value); } catch { process.exit(1); }
    if (!["http:", "https:"].includes(url.protocol) || url.origin !== value.replace(/\/$/, "") || url.username || url.password) process.exit(1);
  ' "$1" || { echo "error: $2 must be an HTTP(S) origin without a path, query, fragment, or credentials: $1" >&2; exit 2; }
}

validate_origin "$WEB_ORIGIN" "frontend URL"
validate_origin "$INTERNAL_API_ORIGIN" "internal backend URL"
WEB_ORIGIN="$(node -e 'process.stdout.write(new URL(process.argv[1]).origin)' "$WEB_ORIGIN")"
INTERNAL_API_ORIGIN="$(node -e 'process.stdout.write(new URL(process.argv[1]).origin)' "$INTERNAL_API_ORIGIN")"
DEV_ALLOWED_HOST="$(node -e 'process.stdout.write(new URL(process.argv[1]).host)' "$WEB_ORIGIN")"

export SPRING_PROFILES_ACTIVE=dev
export CONTEXTMESH_DEV_ALLOWED_ORIGIN_HOST="$DEV_ALLOWED_HOST"
# Server-side only: Next.js proxies same-origin /api/** here, so the browser needs no backend origin.
export CONTEXTMESH_INTERNAL_API_ORIGIN="$INTERNAL_API_ORIGIN"

echo "ContextMesh development startup"
echo "  Frontend:         $WEB_ORIGIN"
echo "  API path:         /api (same-origin, proxied by Next.js)"
echo "  Internal backend: $INTERNAL_API_ORIGIN (not required to be reachable from the browser)"

cd "$ROOT_DIR"
docker compose up -d postgres
if [[ ! -d apps/web/node_modules ]]; then
  echo "Installing frontend dependencies..."
  (cd apps/web && npm ci)
fi

pids=()
names=()
cleanup() {
  trap - EXIT INT TERM
  ((${#pids[@]})) && kill "${pids[@]}" 2>/dev/null || true
  ((${#pids[@]})) && wait "${pids[@]}" 2>/dev/null || true
}
trap cleanup EXIT INT TERM

(cd services/server && exec ./mvnw spring-boot:run) &
pids+=("$!"); names+=("server")
(cd apps/web && exec npm run dev) &
pids+=("$!"); names+=("web")

set +e
wait -n -p exited_pid "${pids[@]}"
status=$?
set -e
failed="child process"
for i in "${!pids[@]}"; do
  [[ "${pids[$i]}" == "${exited_pid:-}" ]] && failed="${names[$i]}"
done
echo "error: $failed exited with status $status; stopping development stack" >&2
((status == 0)) && status=1
exit "$status"
