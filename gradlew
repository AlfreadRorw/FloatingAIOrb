#!/bin/sh
set -eu

GRADLE_VERSION="8.9"
CACHE_DIR="${GRADLE_USER_HOME:-$HOME/.gradle}/wrapper/dists/custom-gradle-${GRADLE_VERSION}"
GRADLE_HOME="$CACHE_DIR/gradle-${GRADLE_VERSION}"

if [ ! -x "$GRADLE_HOME/bin/gradle" ]; then
  mkdir -p "$CACHE_DIR"
  ARCHIVE="$CACHE_DIR/gradle-${GRADLE_VERSION}-bin.zip"
  if [ ! -f "$ARCHIVE" ]; then
    if command -v curl >/dev/null 2>&1; then
      curl -fL --retry 3 -o "$ARCHIVE" "https://services.gradle.org/distributions/gradle-${GRADLE_VERSION}-bin.zip"
    elif command -v wget >/dev/null 2>&1; then
      wget -O "$ARCHIVE" "https://services.gradle.org/distributions/gradle-${GRADLE_VERSION}-bin.zip"
    else
      echo "curl or wget is required to bootstrap Gradle" >&2
      exit 1
    fi
  fi
  rm -rf "$GRADLE_HOME" "$CACHE_DIR/gradle-${GRADLE_VERSION}"
  unzip -q "$ARCHIVE" -d "$CACHE_DIR"
fi

exec "$GRADLE_HOME/bin/gradle" "$@"
