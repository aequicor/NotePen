#!/usr/bin/env bash
# SessionStart hook. Prints a short context block — branch, uncommitted, Kotlin/Gradle versions.

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

cat <<EOF
[session-start]
branch: $branch
uncommitted files: $status_lines
gradle: $gradle_version
kotlin: $kotlin_version
EOF
