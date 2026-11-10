#!/usr/bin/env bash
set -e; cd "$(dirname "$0")/.."; mkdir -p out; javac --release 17 -encoding UTF-8 -d out $(find src/main/java -name '*.java'); echo "Built Lab1-Chat in out/"
