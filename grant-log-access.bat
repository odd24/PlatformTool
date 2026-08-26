@echo off
setlocal EnableExtensions

set "ADB=%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe"
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

echo [1/3] Waiting for Android device...
"%ADB%" wait-for-device
if errorlevel 1 goto :failed

echo [2/3] Granting persistent READ_LOGS permission...
"%ADB%" shell pm grant com.example.platformtool android.permission.READ_LOGS
if errorlevel 1 goto :failed

echo [3/3] Restarting PlatformTool...
"%ADB%" shell am force-stop com.example.platformtool
"%ADB%" shell monkey -p com.example.platformtool -c android.intent.category.LAUNCHER 1 >nul

echo Persistent log access granted successfully.
echo Main, System, Radio, Events, Crash and All can now refresh continuously.
pause
exit /b 0

:failed
echo [ERROR] READ_LOGS could not be granted on this device.
echo Use a rooted build or install PlatformTool as a privileged system app.
pause
exit /b 1
