#!/usr/bin/env bash
set -e
cd "$(dirname "$0")/.."
if [ ! -f out/lab1/chat/ServerDashboardMain.class ] || find src/main/java -name '*.java' -newer out/lab1/chat/ServerDashboardMain.class | read -r _; then
  bash scripts/build.sh
fi
java -cp out lab1.chat.ServerDashboardMain "${@}"
