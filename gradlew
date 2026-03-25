#!/bin/sh

#
# Gradle start up script for UN*X
#

# Determine the Java command to use to start the JVM.
if [ -n "$JAVA_HOME" ] ; then
    JAVACMD="$JAVA_HOME/bin/java"
else
    JAVACMD="java"
fi

# Resolve links: $0 may be a link
PRG="$0"
while [ -h "$PRG" ] ; do
    ls=`ls -ld "$PRG"`
    link=`expr "$ls" : '.*-> \(.*\)$'`
    if expr "$link" : '/.*' > /dev/null; then
        PRG="$link"
    else
        PRG=`dirname "$PRG"`"/$link"
    fi
done
PRGDIR=`dirname "$PRG"`
APP_HOME=`cd "$PRGDIR" > /dev/null && pwd`

APP_NAME="Gradle"
APP_BASE_NAME=`basename "$0"`

DEFAULT_JVM_OPTS='"-Xmx64m" "-Xms64m"'

CLASSPATH="$APP_HOME/gradle/wrapper/gradle-wrapper.jar"

exec "$JAVACMD" \
  -Xmx64m \
  -Xms64m \
  -classpath "$CLASSPATH" \
  "-Dorg.gradle.appname=$APP_BASE_NAME" \
  org.gradle.wrapper.GradleWrapperMain "$@"
