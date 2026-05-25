#!/usr/bin/env bash
# SessionStart hook. Prints a one-line context block for the KMP+Ktor project.

set -euo pipefail

branch="$(git rev-parse --abbrev-ref HEAD 2>/dev/null || echo '(no git)')"
status_lines="$(git status --porcelain 2>/dev/null | wc -l | tr -d ' ' || echo '0')"

gradle_version="(no wrapper)"
if [ -f "./gradlew" ]; then
  gradle_version="$(./gradlew --version 2>/dev/null | sed -n 's/^Gradle \(.*\)$/\1/p' | head -n1)"
  [ -z "$gradle_version" ] && gradle_version="(unknown)"
fi

kotlin_version="(unknown)"
if [ -f "gradle/libs.versions.toml" ]; then
  kotlin_version="$(sed -n 's/^kotlin[[:space:]]*=[[:space:]]*"\([^"]*\)".*/\1/p' gradle/libs.versions.toml | head -n1)"
  [ -z "$kotlin_version" ] && kotlin_version="(not in libs.versions.toml)"
fi

# Bounded scan of build files to detect KMP and Ktor.
scan="$(find . -maxdepth 3 \( -name '*.gradle.kts' -o -name 'libs.versions.toml' \) -not -path '*/build/*' 2>/dev/null || true)"
kmp="no"
ktor="no"
if [ -n "$scan" ]; then
  if printf '%s\n' "$scan" | xargs grep -lE 'multiplatform' >/dev/null 2>&1; then kmp="yes"; fi
  if printf '%s\n' "$scan" | xargs grep -liE 'ktor' >/dev/null 2>&1; then ktor="yes"; fi
fi

echo "[session-start] branch: ${branch} | uncommitted: ${status_lines} | gradle: ${gradle_version} | kotlin: ${kotlin_version} | kmp: ${kmp} | ktor: ${ktor}"
