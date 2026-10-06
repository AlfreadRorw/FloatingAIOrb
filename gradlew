#!/bin/sh
# ALF Vision Panel Gradle launcher
# GitHub Actions installs Gradle 8.9 before this script is called.
if command -v gradle >/dev/null 2>&1; then
    exec gradle "$@"
fi

echo "ERROR: Gradle tidak ditemukan di PATH."
echo "Pastikan workflow menjalankan gradle/actions/setup-gradle@v4 terlebih dahulu."
exit 1
