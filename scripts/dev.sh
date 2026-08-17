#!/usr/bin/env bash
set -Eeuo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
WEB_ORIGIN="${CONTEXTMESH_PUBLIC_WEB_ORIGIN:-http://localhost:3000}"
API_ORIGIN="${CONTEXTMESH_PUBLIC_API_ORIGIN:-http://localhost:8080}"

if { [[ -n "${CONTEXTMESH_PUBLIC_WEB_ORIGIN:-}" ]] && [[ -z "${CONTEXTMESH_PUBLIC_API_ORIGIN:-}" ]]; } ||
   { [[ -z "${CONTEXTMESH_PUBLIC_WEB_ORIGIN:-}" ]] && [[ -n "${CONTEXTMESH_PUBLIC_API_ORIGIN:-}" ]]; }; then
  echo "error: CONTEXTMESH_PUBLIC_WEB_ORIGIN and CONTEXTMESH_PUBLIC_API_ORIGIN must be set together" >&2
  exit 2
fi

validate_origin() {
  node -e '
    const value = process.argv[1];
    let url;
    try { url = new URL(value); } catch { process.exit(1); }
    if (!["http:", "https:"].includes(url.protocol) || url.origin !== value.replace(/\/$/, "") || url.username || url.password) process.exit(1);
  ' "$1" || { echo "error: $2 must be an HTTP(S) origin without a path, query, fragment, or credentials: $1" >&2; exit 2; }
}

validate_origin "$WEB_ORIGIN" "frontend URL"
validate_origin "$API_ORIGIN" "backend URL"
WEB_ORIGIN="$(node -e 'process.stdout.write(new URL(process.argv[1]).origin)' "$WEB_ORIGIN")"
API_ORIGIN="$(node -e 'process.stdout.write(new URL(process.argv[1]).origin)' "$API_ORIGIN")"
DEV_ALLOWED_HOST="$(node -e 'process.stdout.write(new URL(process.argv[1]).host)' "$WEB_ORIGIN")"

export SPRING_PROFILES_ACTIVE=dev
export NEXT_PUBLIC_API_BASE_URL="$API_ORIGIN"
export CONTEXTMESH_DEV_ALLOWED_ORIGIN_HOST="$DEV_ALLOWED_HOST"
export CONTEXTMESH_DEV_ALLOWED_ORIGINS="$WEB_ORIGIN"

echo "ContextMesh development startup"
echo "  Frontend: $WEB_ORIGIN"
echo "  Backend:  $API_ORIGIN"

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
