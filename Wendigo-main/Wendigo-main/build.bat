@echo off

if not exist "%bin/%" mkdir "%bin/%"
echo Building..
javac.exe src/*.java -d bin/
echo Built!
