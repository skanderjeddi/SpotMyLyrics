#!/bin/zsh
./cleanup.zsh
./compile.zsh
cd ../bin/
find . -name "*.class" > ../scripts/classes.txt
jar cvfm SpotMyLyrics.jar ../MANIFEST @../scripts/classes.txt
mv SpotMyLyrics.jar ../