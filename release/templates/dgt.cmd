@echo off
setlocal

set "SCRIPT_DIR=%~dp0"
rem Remove trailing backslash
if "%SCRIPT_DIR:~-1%"=="\" set "SCRIPT_DIR=%SCRIPT_DIR:~0,-1%"

set "UBER_JAR=%SCRIPT_DIR%\{{uber-jar}}"

if not exist "%UBER_JAR%" (
    echo Error: dialog-tool jar not found at %UBER_JAR% >&2
    exit /b 1
)

java --enable-native-access=ALL-UNNAMED ^
  --class-path "%UBER_JAR%" ^
  clojure.main -m dialog-tool.main %*
