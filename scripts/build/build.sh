#!/bin/sh
./cleanup.sh
./compile.sh
cd ../../bin/
find . -name "*.class" > ../scripts/build/classes.txt
jar cvfm SpotMyLyrics.jar ../MANIFEST @../scripts/build/classes.txt
mv SpotMyLyrics.jar ../