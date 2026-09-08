#!/usr/bin/env bash
# Espelha .github/workflows/api.yml (lint → build → migrate).
set -euo pipefail

root="$(cd "$(dirname "$0")/.." && pwd)"
cd "$root"

export DATABASE_URL="${DATABASE_URL:-postgres://postgres:password@localhost:5436/olivia}"
export BETTER_AUTH_SECRET="${BETTER_AUTH_SECRET:-ci-placeholder-secret-32-characters}"
export BETTER_AUTH_URL="${BETTER_AUTH_URL:-http://localhost:3000}"
export CORS_ORIGIN="${CORS_ORIGIN:-http://localhost:3000}"
export NODE_ENV="${NODE_ENV:-test}"

bun install --frozen-lockfile
bun run lint
bun run build --filter=server
bun run db:migrate
