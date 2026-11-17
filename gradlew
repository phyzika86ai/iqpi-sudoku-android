#!/bin/sh
APP_HOME=$(cd "$(dirname "$0")" && pwd)
WRAPPER="$APP_HOME/gradle/wrapper/gradle-wrapper.jar"
if [ ! -f "$WRAPPER" ]; then
  echo "Missing Gradle wrapper. Download official Gradle 8.9 wrapper jar to $WRAPPER" >&2
  exit 1
fi
if [ -n "$JAVA_HOME" ]; then JAVA="$JAVA_HOME/bin/java"; else JAVA=java; fi
exec "$JAVA" -Xmx64m -cp "$WRAPPER" org.gradle.wrapper.GradleWrapperMain "$@"
