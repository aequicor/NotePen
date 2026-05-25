#!/usr/bin/env bash
# Stop hook. Runs detekt before session ends. Prints first violation on failure.

set -euo pipefail

if [ ! -x "./gradlew" ]; then
  exit 0
fi

out_file="$(mktemp -t detekt-stop.XXXXXX)"
trap 'rm -f "$out_file"' EXIT

if ./gradlew detekt --quiet >"$out_file" 2>&1; then
  exit 0
fi

echo "[stop-detekt] ./gradlew detekt failed. First lines of output:" >&2
head -n 30 "$out_file" >&2
exit 0
