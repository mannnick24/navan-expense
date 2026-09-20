#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
JAR="$ROOT/api/build/libs/navan-expense.jar"
if [[ ! -f "$JAR" ]]; then
  (cd "$ROOT" && ./gradlew :api:bootJar)
fi
exec java -jar "$JAR" "$@"
