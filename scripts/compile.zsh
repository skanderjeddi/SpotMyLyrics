#!/bin/zsh
cd ../
find . -name "*.java" > ./scripts/sources.txt
javac -d bin/ @./scripts/sources.txt