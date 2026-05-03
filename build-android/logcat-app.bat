@echo off
setlocal

set "ADB=adb"
where adb >nul 2>&1
if %ERRORLEVEL% NEQ 0 if defined ANDROID_HOME if exist "%ANDROID_HOME%\platform-tools\adb.exe" set "ADB=%ANDROID_HOME%\platform-tools\adb.exe"
if "%ADB%"=="adb" if defined ANDROID_SDK_ROOT if exist "%ANDROID_SDK_ROOT%\platform-tools\adb.exe" set "ADB=%ANDROID_SDK_ROOT%\platform-tools\adb.exe"
if "%ADB%"=="adb" if exist "%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe" set "ADB=%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe"

"%ADB%" logcat | findstr /i "python chromium io.github.naitlee.catprinter AndroidRuntime"

