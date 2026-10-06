#!/usr/bin/env sh
set -eu
if command -v gradle >/dev/null 2>&1; then
  exec gradle "$@"
fi
echo "Gradle belum tersedia. Jalankan build melalui GitHub Actions."
exit 1
