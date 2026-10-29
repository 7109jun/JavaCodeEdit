#!/bin/sh
# claude-ai.jar 만들기 (JDK 21 필요). 사용법: sh build.sh
set -e
HERE="$(cd "$(dirname "$0")" && pwd)"
cd "$HERE"
rm -rf out && mkdir out
javac -encoding UTF-8 -cp ../classes -d out ClaudeAiPlugin.java
jar --create --file claude-ai.jar --manifest manifest.txt -C out .
rm -rf out
echo "만들었습니다: claude-ai.jar"
