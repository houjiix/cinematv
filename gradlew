#!/bin/sh
# Gradle start up script for UN*X (CINEMA TV)
set -e
APP_HOME=$( cd "${APP_HOME:-./}" && pwd -P )
CLASSPATH=$APP_HOME/gradle/wrapper/gradle-wrapper.jar
if [ ! -f "$CLASSPATH" ]; then
  echo "ERROR: gradle-wrapper.jar not found at $CLASSPATH. Run 'gradle wrapper --gradle-version 8.11.1' to bootstrap." >&2
  exit 1
fi
exec "${JAVA_HOME:+$JAVA_HOME/bin/}java" \
  -Xmx64m -Xms64m \
  -Dorg.gradle.appname="gradlew" \
  -classpath "$CLASSPATH" \
  org.gradle.wrapper.GradleWrapperMain "$@"
