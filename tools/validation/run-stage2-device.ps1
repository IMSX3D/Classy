param(
    [string]$Serial = "emulator-5554",
    [string]$OutputDirectory = (Join-Path $PSScriptRoot "out")
)
$ErrorActionPreference = "Stop"
if ($env:ANDROID_HOME) {
    $adb = Join-Path $env:ANDROID_HOME "platform-tools/adb.exe"
} else {
    $cmd = Get-Command adb -ErrorAction SilentlyContinue
    if (-not $cmd) { throw "找不到 adb：请设置 ANDROID_HOME，或把 platform-tools 加入 PATH。" }
    $adb = $cmd.Source
}
if ($Serial -notmatch '^emulator-[0-9]+$') {
    throw "This harness is restricted to isolated emulators. Do not run fixture tests on a personal device."
}
New-Item -ItemType Directory -Path $OutputDirectory -Force | Out-Null
& $adb -s $Serial get-state
if ($LASTEXITCODE -ne 0) { throw "Emulator is not available" }
& $adb -s $Serial install -r "app/build/outputs/apk/debug/app-x86_64-debug.apk"
if ($LASTEXITCODE -ne 0) { throw "App installation failed" }
& $adb -s $Serial install -r "app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk"
if ($LASTEXITCODE -ne 0) { throw "Test installation failed" }
& $adb -s $Serial shell getprop ro.build.fingerprint | Set-Content (Join-Path $OutputDirectory "fingerprint.txt")
& $adb -s $Serial shell wm size | Set-Content (Join-Path $OutputDirectory "size.txt")
& $adb -s $Serial shell wm density | Set-Content (Join-Path $OutputDirectory "density.txt")
$result = & $adb -s $Serial shell am instrument -w -r -e class com.imsx3d.classy.ui.InteractionStageTwoTest com.imsx3d.classy.debug.test/androidx.test.runner.AndroidJUnitRunner 2>&1
$result | Set-Content (Join-Path $OutputDirectory "interaction-tests.txt") -Encoding utf8
$result | Write-Output
if (-not (($result -join [Environment]::NewLine).Contains('OK (4 tests)'))) { throw "Device assertions did not all pass; inspect interaction-tests.txt" }
