param(
    [string]$Adb = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"
)

$remoteScript = "/data/local/tmp/platformtool-kernel-bridge.sh"
if (-not (Test-Path -LiteralPath $Adb)) { $Adb = "adb" }

$dashCResult = (& $Adb shell su -c id 2>&1 | Out-String)
if ($dashCResult -match "uid=0") {
    & $Adb shell su -c "sh $remoteScript stop"
} else {
    & $Adb shell su 0 sh $remoteScript stop
}
