#!/usr/bin/env bash
# UserPromptSubmit hook (macOS / POSIX): when the user's prompt mentions "ai-vision"
# (case-insensitive), inject context telling Claude to DO the visual work itself using the
# macOS UX-testing harness under .claude/tools/ (notepen-android helper + Roborazzi).
#
# Wired from .claude/settings.local.json (gitignored, per-machine). Install with:
#   node .claude/tools/install-ai-vision-hook.mjs
#
# Reads the hook payload (JSON) on stdin; on a match, prints context to stdout (which Claude
# adds to the turn's context for UserPromptSubmit) and exits 0. No match -> prints nothing.
set -euo pipefail

payload="$(cat)"
# The keyword only ever appears in the user's prompt; a raw match keeps this dependency-free.
if ! printf '%s' "$payload" | grep -iq 'ai-vision'; then
  exit 0
fi

cat <<'CTX'
The user's message contains the keyword "ai-vision". This is a STANDING INSTRUCTION: do the visual / UI
work yourself and SHOW the result -- real screenshots, a recorded animation, and a frame-by-frame
filmstrip -- not just a description. Read/display the captured PNG artifacts in your reply.

Use the macOS UX-testing harness under .claude/tools/ (read .claude/tools/README.md first). Pick the
platform(s) relevant to the request:

- Android (device or emulator): `.claude/tools/bin/notepen-android` (boot-emulator, install, launch,
  shot <file.png> [max], tap, swipe, text, key, rotate). For an animation, loop
  `.claude/tools/bin/notepen-android shot frameNNN.png` during the transition.
- Filmstrip / GIF: the ordered frame PNGs ARE the раскадровка -- read them in sequence to show motion.
  To assemble a single animated GIF or a composite filmstrip image, use ffmpeg or ImageMagick
  (`montage` / `convert`) if installed (brew install ffmpeg imagemagick); otherwise present the numbered
  frame sequence directly.
- Deterministic desktop snapshots (no GUI/permissions): Roborazzi
  `./gradlew :reflow:impl:jvmTest --tests "*SnapshotTest"`.

Actually run the tools, capture frames, and display the artifacts -- do not merely explain what could be done.
CTX
exit 0
