@echo off
setlocal

set "SCRIPT_DIR=%~dp0"
set "APK="
set "ADB=adb"
set "APP_ID=io.github.naitlee.catprinter"
set "APP_ACTIVITY=org.kivy.android.PythonActivity"
set "INSTALL_LOG=%TEMP%\cat-printer-adb-install.log"

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
"%ADB%" wait-for-device >nul
echo Installing "%APK%"
"%ADB%" install -r "%APK%" > "%INSTALL_LOG%" 2>&1
set "INSTALL_ERROR=%ERRORLEVEL%"
type "%INSTALL_LOG%"
if %INSTALL_ERROR% NEQ 0 (
    findstr /c:"INSTALL_FAILED_UPDATE_INCOMPATIBLE" "%INSTALL_LOG%" >nul
    if %ERRORLEVEL% NEQ 0 exit /b %INSTALL_ERROR%
    echo Existing %APP_ID% install has a different signature. Removing it and retrying...
    "%ADB%" uninstall %APP_ID%
    if %ERRORLEVEL% NEQ 0 exit /b %ERRORLEVEL%
    "%ADB%" install "%APK%"
    if %ERRORLEVEL% NEQ 0 exit /b %ERRORLEVEL%
)

"%ADB%" shell am force-stop %APP_ID%
echo Launching %APP_ID%/%APP_ACTIVITY%
"%ADB%" shell am start -W -n %APP_ID%/%APP_ACTIVITY%
exit /b %ERRORLEVEL%

