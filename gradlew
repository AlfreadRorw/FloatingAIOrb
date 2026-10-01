#!/usr/bin/env sh
set -eu
if command -v gradle >/dev/null 2>&1; then
  exec gradle "$@"
fi
printf '%s\n' "Gradle is not installed on this machine. Install Gradle 8.9+ or use Android Studio/GitHub Actions." >&2
exit 1
