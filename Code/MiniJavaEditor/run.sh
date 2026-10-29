#!/bin/sh
# Linux / macOS launcher. Needs a JDK 21 (a "jdk*" folder next to this file, JAVA_HOME, or java on PATH).
HERE="$(cd "$(dirname "$0")" && pwd)"
JAVA=""
for d in "$HERE"/jdk*; do
  [ -x "$d/bin/java" ] && JAVA="$d/bin/java"
  [ -x "$d/Contents/Home/bin/java" ] && JAVA="$d/Contents/Home/bin/java"
done
[ -z "$JAVA" ] && [ -n "$JAVA_HOME" ] && [ -x "$JAVA_HOME/bin/java" ] && JAVA="$JAVA_HOME/bin/java"
[ -z "$JAVA" ] && JAVA="$(command -v java)"
if [ -z "$JAVA" ]; then echo "JDK 21 was not found. See README.txt"; exit 1; fi
exec "$JAVA" -Dfile.encoding=UTF-8 -cp "$HERE/classes" MiniJavaEditor "$@"
