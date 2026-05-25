#!/usr/bin/env bash
# PreToolUse hook for Bash. Inspects `git commit` against the staged diff.
# Blocks (exit 2) on secrets / key material. Warns (exit 0) on TODO/FIXME and !!.
# Receives tool input as JSON on stdin.

set -euo pipefail

input="$(cat)"
cmd="$(printf '%s' "$input" | sed -n 's/.*"command"[[:space:]]*:[[:space:]]*"\([^"]*\)".*/\1/p')"
[ -z "$cmd" ] && exit 0

# Only guard actual commit commands.
case "$cmd" in
  *"git commit"*) ;;
  *) exit 0 ;;
esac

diff="$(git diff --cached --no-color 2>/dev/null || true)"
[ -z "$diff" ] && exit 0

# Added lines only (start with single +, exclude the +++ file header).
added="$(printf '%s\n' "$diff" | grep -E '^\+' | grep -vE '^\+\+\+' || true)"
[ -z "$added" ] && exit 0

blockers=""
warnings=""

# --- Blockers: secrets / key material ---
if printf '%s\n' "$added" | grep -qE -- '-----BEGIN [A-Z ]*PRIVATE KEY-----'; then
  blockers="${blockers}\n  - private key material"
fi
if printf '%s\n' "$added" | grep -qE 'AKIA[0-9A-Z]{16}'; then
  blockers="${blockers}\n  - AWS access key id"
fi
if printf '%s\n' "$added" | grep -qiE '(password|secret|api[_-]?key|access[_-]?token|auth[_-]?token|client[_-]?secret)[[:space:]]*[:=][[:space:]]*["'\''][^"'\'' ]{6,}'; then
  blockers="${blockers}\n  - hardcoded credential/secret literal"
fi

# --- Warnings: debug leftovers / forbidden constructs ---
if printf '%s\n' "$added" | grep -qE '(//[[:space:]]*(TODO|FIXME)|\bTODO\()'; then
  warnings="${warnings}\n  - TODO/FIXME left in staged code"
fi
if printf '%s\n' "$added" | grep -qE '!![.,)[:space:]]'; then
  warnings="${warnings}\n  - !! non-null assertion in staged code"
fi

if [ -n "$warnings" ]; then
  echo "[guard-commit] warning — staged diff contains:" >&2
  printf '%b\n' "$warnings" >&2
fi

if [ -n "$blockers" ]; then
  echo "BLOCKED by guard-commit: staged diff contains secrets:" >&2
  printf '%b\n' "$blockers" >&2
  echo "Remove the secret, move it to env / local.properties (gitignored), and unstage it. Do not bypass." >&2
  exit 2
fi

exit 0
