@echo off
setlocal
cd /d "%~dp0"
if "%~1"=="" (
  echo Usar: criar-admin.cmd email@exemplo.pt
  exit /b 1
)
if not exist config.properties (
  echo Copia config.example.properties para config.properties e configura o MySQL.
  exit /b 1
)
call compilar.cmd
if errorlevel 1 exit /b 1
java --add-modules jdk.httpserver -cp "build;lib/*" exemplo.App --criar-admin "%~1"
