# Yeti TV dev loop: build -> install on the box over USB adb -> open home -> screenshot.
# Usage:  powershell -ExecutionPolicy Bypass -File tools\dev.ps1            (build + install + screenshots)
#         powershell -ExecutionPolicy Bypass -File tools\dev.ps1 -NoBuild   (install the last build again)
param([switch]$NoBuild)
$root = Split-Path $PSScriptRoot -Parent
Set-Location $root
if (-not $NoBuild) { & .\gradlew.bat assembleRelease; if ($LASTEXITCODE -ne 0) { exit 1 } }
$apk = Get-ChildItem "app\build\outputs\apk\release\*.apk" -ErrorAction SilentlyContinue | Select-Object -First 1
if (-not $apk) { Write-Host "No build found - run without -NoBuild first" -ForegroundColor Red; exit 1 }
adb root | Out-Null; Start-Sleep 2; adb wait-for-device
adb install -r $apk.FullName
# Full AOT compile: the home screen is the first app Android waits for at boot, so cold start matters
adb shell "cmd package compile -m speed -f tv.yeti.launcher" | Out-Null
# Wi-Fi picker: scan results + SSID need location (the picker also asks, this just skips the prompt on the dev box)
adb shell "pm grant tv.yeti.launcher android.permission.ACCESS_FINE_LOCATION" 2>$null
# Sound / Date & time toggles (system sound effects, 24-hour clock, automatic time)
adb shell "appops set tv.yeti.launcher WRITE_SETTINGS allow; pm grant tv.yeti.launcher android.permission.WRITE_SECURE_SETTINGS; pm grant tv.yeti.launcher android.permission.INTERACT_ACROSS_USERS" 2>$null
adb shell "cmd package set-home-activity tv.yeti.launcher/.MainActivity; am force-stop tv.yeti.launcher; input keyevent KEYCODE_HOME"
$shots = Join-Path $root "shots"; New-Item -ItemType Directory -Force $shots | Out-Null
function Shot($n) { Start-Sleep 3; adb shell "screencap -p /data/local/tmp/$n.png"; adb pull "/data/local/tmp/$n.png" "$shots\$n.png" | Out-Null; Write-Host "shots\$n.png" }
Shot "home"
adb shell "input keyevent KEYCODE_DPAD_UP"; Shot "home_quickbar"
adb shell "am start -n tv.yeti.launcher/.AppsActivity"; Shot "all_apps"
adb shell "am start -n tv.yeti.launcher/.AboutActivity"; Shot "about"
adb shell "am start -n tv.yeti.launcher/.WifiActivity"; Shot "wifi"
foreach ($p in "Network","Display","Sound","DateTime","AppSettings") { adb shell "am start -n tv.yeti.launcher/.${p}Activity"; Shot ($p.ToLower()) }
adb shell "input keyevent KEYCODE_HOME"
