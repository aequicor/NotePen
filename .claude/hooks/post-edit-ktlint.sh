#!/usr/bin/env bash
# PostToolUse hook for Edit|Write. Auto-formats the single edited Kotlin file with ktlint.
# Receives tool input as JSON on stdin. Silent for non-Kotlin files.
#
# Formats ONLY the edited file, via the root project's `ktlintFormatFile` task (it runs the
# ktlint CLI's `-F` on exactly one path). ktlint-gradle has no single-file task, and its
# per-source-set format tasks reformat every file in the set — which would touch unrelated,
# possibly in-progress files. Driving the CLI on one path avoids that.

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

# Skip if there's no Gradle wrapper — fall back to a standalone ktlint binary if present.
if [ ! -x "./gradlew" ]; then
  if command -v ktlint >/dev/null 2>&1; then
    ktlint -F "$file_path" >/dev/null 2>&1 || true
  fi
  exit 0
fi

# ktlint resolves its rules from .editorconfig relative to the file; an absolute path is fine.
# Configuration cache is disabled because the -P value changes per edit, which would otherwise
# churn the cache on every invocation.
if ! ./gradlew ktlintFormatFile -PktlintFile="$file_path" --no-configuration-cache --quiet >/dev/null 2>&1; then
  echo "[post-edit-ktlint] ktlint could not auto-format $file_path — run ./gradlew ktlintCheck for details." >&2
fi

exit 0
