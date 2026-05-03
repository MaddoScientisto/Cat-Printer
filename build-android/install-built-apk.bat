@echo off
setlocal

set "SCRIPT_DIR=%~dp0"
set "APK="
set "ADB=adb"

where adb >nul 2>&1
if %ERRORLEVEL% NEQ 0 if defined ANDROID_HOME if exist "%ANDROID_HOME%\platform-tools\adb.exe" set "ADB=%ANDROID_HOME%\platform-tools\adb.exe"
if "%ADB%"=="adb" if defined ANDROID_SDK_ROOT if exist "%ANDROID_SDK_ROOT%\platform-tools\adb.exe" set "ADB=%ANDROID_SDK_ROOT%\platform-tools\adb.exe"
if "%ADB%"=="adb" if exist "%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe" set "ADB=%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe"

for /f "delims=" %%F in ('dir /b /o-d "%SCRIPT_DIR%out\*.apk" 2^>nul') do (
    set "APK=%SCRIPT_DIR%out\%%F"
    goto :install
)

echo No APK found in %SCRIPT_DIR%out 1>&2
exit /b 1

:install
"%ADB%" install -r "%APK%"
exit /b %ERRORLEVEL%

