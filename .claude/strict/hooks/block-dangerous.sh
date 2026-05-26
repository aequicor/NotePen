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
  *"git worktree remove --force"*|*"git worktree remove -f"*)
    block "git worktree remove --force can delete another session's worktree (and its uncommitted work)"
    ;;
  *"git worktree prune"*)
    block "git worktree prune can drop another session's worktree registration — list first with 'git worktree list'"
    ;;
  *"chmod 777"*)
    block "chmod 777 is not allowed"
    ;;
  *"./gradlew --stop"*)
    # allowed — sanitary, not destructive
    ;;
  *"rm -rf .gradle"*|*"rm -rf build"*|*"rm -rf ~/.gradle"*|*"rm -rf ~/.konan"*)
    block "manual gradle/konan cache wipe is not allowed — these are shared across worktrees (use ./gradlew clean)"
    ;;
esac

exit 0
