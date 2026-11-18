@echo off
setlocal
cd /d "%~dp0"
if not exist build mkdir build
javac -encoding UTF-8 --release 17 --add-modules jdk.httpserver -d build src\exemplo\*.java
if errorlevel 1 exit /b 1
echo Compilacao concluida.
