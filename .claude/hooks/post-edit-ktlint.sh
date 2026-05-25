#!/usr/bin/env bash
# PostToolUse hook for Edit|Write. Auto-formats the edited Kotlin file with ktlint.
# Receives tool input as JSON on stdin. Silent for non-Kotlin files.
#
# Only the single edited file is formatted (never the whole tree): we pass it through
# ktlint-gradle's incremental git filter — the same mechanism its git pre-commit
# integration uses — so unrelated files are left untouched.

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

# ktlint-gradle's filter expects a path relative to the git root.
root="$(git rev-parse --show-toplevel 2>/dev/null || echo "$PWD")"
case "$file_path" in
  "$root"/*) rel="${file_path#"$root"/}" ;;
  *) rel="$file_path" ;;
esac

# Format only this file. Configuration cache is disabled because the filter value
# changes per edit, which would otherwise churn the cache on every invocation.
if ! ./gradlew ktlintFormat -PinternalKtlintGitFilter="$rel" --no-configuration-cache --quiet >/dev/null 2>&1; then
  echo "[post-edit-ktlint] ktlint could not auto-format $rel — run ./gradlew ktlintCheck for details." >&2
fi

exit 0
