@echo off
setlocal

set "SCRIPT_DIR=%~dp0"
for %%I in ("%SCRIPT_DIR%..") do set "REPO_ROOT=%%~fI"

if "%~1"=="" (
    if not defined BUILD_MODE set "BUILD_MODE=debug"
) else (
    set "BUILD_MODE=%~1"
)

set "ENV_ARGS="
if exist "%REPO_ROOT%\build-android\.env" set "ENV_ARGS=--env-file build-android\.env"

cd /d "%REPO_ROOT%" || exit /b 1

docker compose version >nul 2>&1
if %ERRORLEVEL% EQU 0 (
    docker compose %ENV_ARGS% -f docker-compose.android.yml up --build --abort-on-container-exit --exit-code-from android-build android-build
) else (
    docker-compose %ENV_ARGS% -f docker-compose.android.yml up --build --abort-on-container-exit --exit-code-from android-build android-build
)

exit /b %ERRORLEVEL%

