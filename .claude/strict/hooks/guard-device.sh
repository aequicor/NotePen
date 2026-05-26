#!/usr/bin/env bash
# PreToolUse hook for Bash. Prevents cross-session device collisions in parallel worktrees by
# blocking adb/simctl commands that don't name a target device.
# Receives tool input as JSON on stdin. Exit 2 = block.

set -euo pipefail

input="$(cat)"
cmd="$(printf '%s' "$input" | sed -n 's/.*"command"[[:space:]]*:[[:space:]]*"\([^"]*\)".*/\1/p')"

if [ -z "$cmd" ]; then
  exit 0
fi

block() {
  echo "BLOCKED by guard-device: $1" >&2
  exit 2
}

# A command is "scoped" if it names a device explicitly, references the lane env vars, or the lane
# is already exported into this environment.
is_scoped() {
  case "$1" in
    *" -s "*|*"--udid"*|*"ANDROID_SERIAL="*|*'$ANDROID_SERIAL'*|*'$IOS_SIM_UDID'*|*'$IOS_SIM_NAME'*)
      return 0
      ;;
  esac
  if [ -n "${ANDROID_SERIAL:-}" ]; then
    return 0
  fi
  return 1
}

# adb device-targeting subcommands. Safe ones (adb devices/start-server/kill-server/version) don't match.
case "$cmd" in
  *"adb shell"*|*"adb install"*|*"adb uninstall"*|*"adb logcat"*|*"adb push"*|*"adb pull"*|*"adb am "*|*"adb forward"*|*"adb reverse"*|*"adb emu"*)
    if ! is_scoped "$cmd"; then
      block "adb targets an unspecified device — add -s \"\$ANDROID_SERIAL\" or export ANDROID_SERIAL via the worktree lane (skill parallel-sessions)"
    fi
    ;;
esac

# simctl against the ambiguous "booted" device, or device ops without an explicit UDID.
case "$cmd" in
  *"simctl "*"booted"*)
    block "xcrun simctl ... 'booted' is ambiguous when several simulators run — target \"\$IOS_SIM_UDID\" explicitly (skill parallel-sessions)"
    ;;
  *"xcrun simctl boot"*|*"xcrun simctl install"*|*"xcrun simctl launch"*|*"xcrun simctl terminate"*)
    if ! is_scoped "$cmd"; then
      block "simctl targets an unspecified simulator — pass \"\$IOS_SIM_UDID\" (skill parallel-sessions)"
    fi
    ;;
esac

exit 0
