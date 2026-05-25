#!/usr/bin/env bash
# PreToolUse hook for Bash. Blocks destructive commands in strict mode.
# Receives tool input as JSON on stdin. Exit 2 = block.

set -euo pipefail

input="$(cat)"
cmd="$(printf '%s' "$input" | sed -n 's/.*"command"[[:space:]]*:[[:space:]]*"\([^"]*\)".*/\1/p')"

if [ -z "$cmd" ]; then
  exit 0
fi

block() {
  echo "BLOCKED by strict-mode hook: $1" >&2
  exit 2
}

case "$cmd" in
  *"rm -rf"*|*"rm -fr"*)
    block "rm -rf is not allowed"
    ;;
  *"git push --force"*|*"git push -f "*|*"git push -f"|*"--force-with-lease"*)
    block "force push is not allowed"
    ;;
  *"git reset --hard"*)
    block "git reset --hard is not allowed (use git reset or stash)"
    ;;
  *"git clean -f"*|*"git clean -fd"*)
    block "git clean -f is not allowed (review files first)"
    ;;
  *"chmod 777"*)
    block "chmod 777 is not allowed"
    ;;
  *"./gradlew --stop"*)
    # allowed — это санитарная операция, не деструктивная
    ;;
  *"rm -rf .gradle"*|*"rm -rf build"*|*"rm -rf ~/.gradle"*)
    block "manual gradle cache wipe is not allowed (use ./gradlew clean)"
    ;;
esac

exit 0
