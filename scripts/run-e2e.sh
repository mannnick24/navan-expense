#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"
if [[ -n "${E2E_BASE_URL:-}" ]]; then
  exec ./gradlew e2eTest --console=plain -Pe2e.baseUrl="$E2E_BASE_URL"
fi
exec ./gradlew e2eTest --console=plain "$@"
