#!/usr/bin/env bash
# Stop hook. Runs detekt (if the task exists) and the test suite before the session ends.
# Informational: surfaces failures on stderr but does not hard-block session close.
# The real commit gate is guard-commit + the pipeline's own discipline.

set -euo pipefail

[ -x "./gradlew" ] || exit 0

out_file="$(mktemp -t flow-stop.XXXXXX)"
trap 'rm -f "$out_file"' EXIT

# detekt — only if the task is present in this project.
if ./gradlew -q help --task detekt >/dev/null 2>&1; then
  if ! ./gradlew detekt --quiet >"$out_file" 2>&1; then
    echo "[stop-checks] ./gradlew detekt failed:" >&2
    head -n 25 "$out_file" >&2
  fi
fi

# tests — prefer allTests (KMP), fall back to test (JVM/Ktor).
test_task="allTests"
if ! ./gradlew -q help --task allTests >/dev/null 2>&1; then
  test_task="test"
fi
if ! ./gradlew "$test_task" --quiet >"$out_file" 2>&1; then
  echo "[stop-checks] ./gradlew $test_task failed:" >&2
  head -n 30 "$out_file" >&2
fi

exit 0
