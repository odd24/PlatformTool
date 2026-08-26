@echo off
setlocal EnableExtensions

set "REMOTE_SCRIPT=/data/local/tmp/platformtool-kernel-bridge.sh"
set "ADB=%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe"

if not exist "%ADB%" (
    where adb >nul 2>nul
    if errorlevel 1 (
        echo [ERROR] adb.exe was not found.
        pause
        exit /b 1
    )
    set "ADB=adb"
)

"%ADB%" wait-for-device
if errorlevel 1 goto :failed

"%ADB%" shell su -c id 2^>^&1 | findstr /c:"uid=0" >nul
if not errorlevel 1 (
    "%ADB%" shell su -c "sh %REMOTE_SCRIPT% stop"
    goto :check_result
)

"%ADB%" shell su 0 id 2^>^&1 | findstr /c:"uid=0" >nul
if not errorlevel 1 (
    "%ADB%" shell su 0 sh "%REMOTE_SCRIPT%" stop
    goto :check_result
)

"%ADB%" shell sh "%REMOTE_SCRIPT%" stop

:check_result
if errorlevel 1 goto :failed
echo Kernel bridge stopped.
pause
exit /b 0

:failed
echo [ERROR] Failed to stop kernel bridge.
pause
exit /b 1
