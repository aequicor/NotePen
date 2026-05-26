#!/usr/bin/env bash
# SessionStart hook. One-line context + this worktree's parallel-session resource lane.

set -euo pipefail

branch="$(git rev-parse --abbrev-ref HEAD 2>/dev/null || echo '(no git)')"
uncommitted="$(git status --porcelain 2>/dev/null | wc -l | tr -d ' ' || echo '0')"

# Worktree detection: a linked worktree's git-dir differs from the shared git-common-dir.
worktree="(main checkout)"
gdir="$(git rev-parse --git-dir 2>/dev/null || echo '')"
cdir="$(git rev-parse --git-common-dir 2>/dev/null || echo '')"
if [ -n "$gdir" ] && [ "$gdir" != "$cdir" ]; then
  worktree="$(basename "$PWD")"
fi

# Lane: prefer the exported ANDROID_SERIAL (direnv / sourced .envrc); else preview from the dir name.
if [ -n "${ANDROID_SERIAL:-}" ]; then
  lane="serial=${ANDROID_SERIAL}${APP_ID_SUFFIX:+ appId${APP_ID_SUFFIX}}${SERVER_PORT:+ server:${SERVER_PORT}}"
else
  slug="$(basename "$PWD")"
  sum="$(printf '%s' "$slug" | cksum 2>/dev/null | cut -d' ' -f1)"
  sum="${sum:-0}"
  id=$(( sum % 50 + 1 ))
  lane="not loaded — would be id=${id} serial=emulator-$(( 5554 + id * 2 )) (load .envrc; see skill parallel-sessions)"
fi

echo "[parallel-work-kmp] worktree: ${worktree} | branch: ${branch} | uncommitted: ${uncommitted} | lane: ${lane}"
