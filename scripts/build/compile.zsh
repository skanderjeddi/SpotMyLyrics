#!/bin/zsh
cd ../../
find . -name "*.java" > ./scripts/build/sources.txt
javac -d bin/ @./scripts/build/sources.txt