@echo off
setlocal EnableExtensions

set "PROJECT_DIR=%~dp0"
set "LOCAL_SCRIPT=%PROJECT_DIR%tools\platformtool-kernel-bridge.sh"
set "REMOTE_SCRIPT=/data/local/tmp/platformtool-kernel-bridge.sh"
set "ADB=%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe"

if not exist "%LOCAL_SCRIPT%" (
    echo [ERROR] Kernel bridge script was not found:
    echo %LOCAL_SCRIPT%
    pause
    exit /b 1
)

if not exist "%ADB%" (
    where adb >nul 2>nul
    if errorlevel 1 (
        echo [ERROR] adb.exe was not found.
        echo Install Android SDK Platform-Tools or add adb to PATH.
        pause
        exit /b 1
    )
    set "ADB=adb"
)

echo [1/5] Waiting for Android device...
"%ADB%" wait-for-device
if errorlevel 1 goto :failed

echo [2/5] Granting persistent log access...
"%ADB%" shell pm grant com.example.platformtool android.permission.READ_LOGS
if errorlevel 1 (
    echo [WARN] READ_LOGS could not be granted. Root mode may still work.
) else (
    "%ADB%" shell am force-stop com.example.platformtool
)

echo [3/5] Uploading kernel bridge...
"%ADB%" push "%LOCAL_SCRIPT%" "%REMOTE_SCRIPT%"
if errorlevel 1 goto :failed
"%ADB%" shell chmod 755 "%REMOTE_SCRIPT%"
if errorlevel 1 goto :failed

echo [4/5] Detecting root command...
"%ADB%" shell su -c id 2^>^&1 | findstr /c:"uid=0" >nul
if not errorlevel 1 (
    echo Using su -c mode.
    "%ADB%" shell su -c "sh %REMOTE_SCRIPT% start"
    goto :check_result
)

"%ADB%" shell su 0 id 2^>^&1 | findstr /c:"uid=0" >nul
if not errorlevel 1 (
    echo Using su 0 mode.
    "%ADB%" shell su 0 sh "%REMOTE_SCRIPT%" start
    goto :check_result
)

echo Root command is unavailable; trying adb shell mode.
"%ADB%" shell sh "%REMOTE_SCRIPT%" start

:check_result
if errorlevel 1 goto :failed
echo [5/5] Kernel and Quick Tools bridges are running.
echo Root Log Viewer and the touch debug switch are now available in PlatformTool.
pause
exit /b 0

:failed
echo [ERROR] Kernel bridge failed to start.
pause
exit /b 1
