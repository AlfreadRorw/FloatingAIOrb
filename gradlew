#!/usr/bin/env sh
set -eu
APP_HOME=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
JAVA_CMD="${JAVA_HOME:-}/bin/java"
if [ ! -x "$JAVA_CMD" ]; then JAVA_CMD="java"; fi
exec "$JAVA_CMD" ${JAVA_OPTS:-} ${GRADLE_OPTS:-} -classpath "$APP_HOME/gradle/wrapper/gradle-wrapper.jar" org.gradle.wrapper.GradleWrapperMain "$@"
