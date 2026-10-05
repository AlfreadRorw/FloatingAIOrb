#!/bin/sh
set -e
GRADLE_VERSION=8.9
CACHE="${GRADLE_USER_HOME:-$HOME/.gradle}/wrapper/dists/gradle-$GRADLE_VERSION-bin"
DIST="$CACHE/gradle-$GRADLE_VERSION"
if [ ! -x "$DIST/bin/gradle" ]; then
  mkdir -p "$CACHE"
  ARCHIVE="$CACHE/gradle.zip"
  if [ ! -f "$ARCHIVE" ]; then
    curl -fsSL "https://services.gradle.org/distributions/gradle-$GRADLE_VERSION-bin.zip" -o "$ARCHIVE"
  fi
  rm -rf "$DIST"
  unzip -q "$ARCHIVE" -d "$CACHE"
fi
exec "$DIST/bin/gradle" "$@"
