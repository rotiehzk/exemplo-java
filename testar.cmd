@echo off
setlocal
cd /d "%~dp0"
call compilar.cmd
if errorlevel 1 exit /b 1
javac -encoding UTF-8 --release 17 --add-modules jdk.httpserver -cp build -d build test\exemplo\SelfTest.java
if errorlevel 1 exit /b 1
java --add-modules jdk.httpserver -cp "build;lib/*" exemplo.SelfTest
