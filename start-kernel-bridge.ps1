param(
    [string]$Adb = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"
)

$ErrorActionPreference = "Stop"
$localScript = Join-Path $PSScriptRoot "tools\platformtool-kernel-bridge.sh"
$remoteScript = "/data/local/tmp/platformtool-kernel-bridge.sh"

if (-not (Test-Path -LiteralPath $Adb)) {
    $Adb = "adb"
}

& $Adb wait-for-device
& $Adb push $localScript $remoteScript
if ($LASTEXITCODE -ne 0) { throw "Failed to push kernel bridge script" }
& $Adb shell chmod 755 $remoteScript

$dashCResult = (& $Adb shell su -c id 2>&1 | Out-String)
if ($dashCResult -match "uid=0") {
    & $Adb shell su -c "sh $remoteScript start"
} else {
    $uidZeroResult = (& $Adb shell su 0 id 2>&1 | Out-String)
    if ($uidZeroResult -match "uid=0") {
        & $Adb shell su 0 sh $remoteScript start
    } else {
        Write-Warning "Root su is unavailable; trying adb shell domain directly."
        & $Adb shell sh $remoteScript start
    }
}

if ($LASTEXITCODE -ne 0) { throw "Kernel bridge failed to start" }
Write-Output "Kernel bridge is running. Open 日志查看器 and select Kernel (dmesg)."
