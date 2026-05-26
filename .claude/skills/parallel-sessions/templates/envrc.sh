# .envrc — per-session resource lane for parallel Claude Code worktrees.
# Self-derives a unique lane from the worktree directory name. No secrets here — safe to commit.
#
# direnv (macOS/Linux/git-bash): place at repo root, then run `direnv allow` once per worktree.
# Without direnv: `source ./.envrc` at the start of work in the worktree.
# Override any value by exporting it BEFORE this file runs (e.g. export WORKTREE_ID=7).

slug="$(basename "$PWD")"

# Stable 1..50 id from the worktree name (collision-rare; export WORKTREE_ID to force a value).
: "${WORKTREE_ID:=$(( $(printf '%s' "$slug" | cksum | cut -d' ' -f1) % 50 + 1 ))}"
export WORKTREE_ID
export WORKTREE_SLUG="$slug"

# Android: emulator console port (even, 5554..5682) + matching adb serial.
# Exporting ANDROID_SERIAL makes adb and ./gradlew install* target THIS emulator automatically.
export ANDROID_EMULATOR_PORT="$(( 5554 + WORKTREE_ID * 2 ))"
export ANDROID_SERIAL="emulator-${ANDROID_EMULATOR_PORT}"
export AVD_NAME="wt-${slug}"

# iOS simulator (macOS only). UDID has no env shortcut like ANDROID_SERIAL — pass it explicitly.
# After `xcrun simctl create "$IOS_SIM_NAME" ...`, capture the UDID into IOS_SIM_UDID.
export IOS_SIM_NAME="wt-${slug}"

# Dev-server / app ports.
export SERVER_PORT="$(( 8080 + WORKTREE_ID ))"
export WASM_PORT="$(( 9090 + WORKTREE_ID ))"
export COMPOSE_DEV_PORT="$(( 9190 + WORKTREE_ID ))"

# applicationId suffix so two branches install side-by-side on one device (empty in main checkout).
export APP_ID_SUFFIX=".wt${WORKTREE_ID}"
