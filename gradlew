#!/bin/sh
set -e
APP_HOME=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
GRADLE_VERSION=8.13
DIST="$HOME/.gradle/alf-wrapper/gradle-$GRADLE_VERSION"
BIN="$DIST/bin/gradle"
if [ ! -x "$BIN" ]; then
  mkdir -p "$DIST"
  TMP="$HOME/.gradle/alf-wrapper/gradle-$GRADLE_VERSION.zip"
  if command -v curl >/dev/null 2>&1; then
    curl -fL --retry 3 -o "$TMP" "https://services.gradle.org/distributions/gradle-$GRADLE_VERSION-bin.zip"
  elif command -v wget >/dev/null 2>&1; then
    wget -O "$TMP" "https://services.gradle.org/distributions/gradle-$GRADLE_VERSION-bin.zip"
  else
    echo "curl or wget is required to bootstrap Gradle $GRADLE_VERSION" >&2
    exit 1
  fi
  rm -rf "$DIST/unpacked"
  mkdir -p "$DIST/unpacked"
  unzip -q "$TMP" -d "$DIST/unpacked"
  cp -R "$DIST/unpacked/gradle-$GRADLE_VERSION/." "$DIST/"
  rm -rf "$DIST/unpacked" "$TMP"
fi
exec "$BIN" -p "$APP_HOME" "$@"
