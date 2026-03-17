#!/bin/sh

APP_HOME=$( cd "${0%"${0##*/}"}." && pwd -P ) || exit
CLASSPATH=$APP_HOME/gradle/wrapper/gradle-wrapper.jar

if [ -n "$JAVA_HOME" ] ; then
    JAVACMD=$JAVA_HOME/bin/java
else
    JAVACMD=java
fi

exec "$JAVACMD" \
  -Dfile.encoding=UTF-8 \
  -Xmx64m \
  -Xms64m \
  $JAVA_OPTS \
  $GRADLE_OPTS \
  -classpath "$CLASSPATH" \
  org.gradle.wrapper.GradleWrapperMain \
  "$@"
