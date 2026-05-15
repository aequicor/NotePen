#!/usr/bin/env bash
# PostToolUse hook for Edit|Write. Auto-formats Kotlin files with ktlint after every edit.
# Receives tool input as JSON on stdin. Silent for non-Kotlin files.

set -euo pipefail

input="$(cat)"
file_path="$(printf '%s' "$input" | sed -n 's/.*"file_path"[[:space:]]*:[[:space:]]*"\([^"]*\)".*/\1/p')"

if [ -z "$file_path" ]; then
  exit 0
fi

case "$file_path" in
  *.kt|*.kts) ;;
  *) exit 0 ;;
esac

# Skip if file no longer exists (e.g. moved/deleted).
[ -f "$file_path" ] || exit 0

# Skip if there's no Gradle wrapper — ktlint integration is project-specific.
if [ ! -x "./gradlew" ]; then
  # Fallback to a standalone ktlint binary on PATH, if any.
  if command -v ktlint >/dev/null 2>&1; then
    ktlint -F "$file_path" >/dev/null 2>&1 || true
  fi
  exit 0
fi

# Run ktlintFormat. Some projects expose only a project-wide task — that is OK,
# it's idempotent and the daemon makes repeat invocations cheap.
./gradlew ktlintFormat --quiet >/dev/null 2>&1 || {
  echo "[post-edit-ktlint] ktlintFormat reported issues that could not be auto-fixed in $file_path — run ./gradlew ktlintCheck for details." >&2
}

exit 0
