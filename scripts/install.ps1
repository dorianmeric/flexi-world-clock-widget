# Install/upload script for Flexi World-Clock Widget.
#
# Installs the signed APK on the connected device (or the running emulator) and
# optionally launches the configuration screen.
#
# Usage:
#   .\scripts\install.ps1                  # adb install -r (+ v4 idsig when present)
#   .\scripts\install.ps1 -Launch          # install, then open the app
#   .\scripts\install.ps1 -Uninstall       # uninstall first
#   .\scripts\install.ps1 -Emulator        # boot the bundled AVD headless if no device
#   .\scripts\install.ps1 -Apk path\to.apk # install a specific APK
#   .\scripts\install.ps1 -Serial emulator-5554  # target one device when several are attached

[CmdletBinding()]
param(
    [string]$Apk,
    [switch]$Launch,
    [switch]$Uninstall,
    [switch]$Emulator,
    [string]$AvdName = 'flexi_api35',
    [string]$Serial
)

$ErrorActionPreference = 'Stop'
$ProgressPreference = 'SilentlyContinue'

$repoRoot = Split-Path -Parent $PSScriptRoot
$sdkDir = if ($env:ANDROID_HOME) { $env:ANDROID_HOME } else { 'C:\P\Android-CLI' }
$adb = Join-Path $sdkDir 'platform-tools\adb.exe'
if (-not (Test-Path $adb)) { $adb = Join-Path $sdkDir 'bin\adb.exe' }
if (-not (Test-Path $adb)) { throw "adb not found under $sdkDir" }

$packageName = 'com.flexi.worldclock'

function Get-ConnectedSerial {
    $lines = & $adb devices | Select-Object -Skip 1
    $serial = ($env:ANDROID_SERIAL, $script:Serial |
        Where-Object { $_ } | Select-Object -First 1)
    foreach ($line in $lines) {
        if ($line -match '^(\S+)\s+device$') {
            # Prefer an explicitly requested device over whichever enumerated first.
            if (-not $serial -or $matches[1] -eq $serial) { return $matches[1] }
        }
    }
    return $null
}

if (-not $Apk) {
    $Apk = Join-Path $repoRoot 'dist\flexi-world-clock-widget.apk'
}
if (-not (Test-Path $Apk)) {
    throw "APK not found: $Apk. Run scripts/build.ps1 first."
}

$serial = Get-ConnectedSerial
if (-not $serial -and $Emulator) {
    Write-Host '==> No device attached, starting the emulator' -ForegroundColor Cyan
    $emulator = Join-Path $sdkDir 'emulator\emulator.exe'
    $avdmanager = Join-Path $sdkDir 'cmdline-tools\latest\bin\avdmanager.bat'
    if (-not (Test-Path $avdmanager)) { $avdmanager = Join-Path $sdkDir 'bin\avdmanager.bat' }

    $avds = & (Join-Path $sdkDir 'emulator\emulator.exe') -list-avds
    if (-not ($avds -contains $AvdName) -and (Test-Path $avdmanager)) {
        Write-Host "==> Creating AVD $AvdName" -ForegroundColor Cyan
        'no' | & $avdmanager create avd -n $AvdName `
            -k 'system-images;android-35;google_apis;x86_64' -d pixel_5 --force
    }

    Start-Process -FilePath $emulator `
        -ArgumentList @('-avd', $AvdName, '-no-window', '-no-audio', '-no-boot-anim',
                            '-gpu', 'swiftshader_indirect') -WindowStyle Hidden `
            -Environment @{
                ANDROID_HOME = $sdkDir
                ANDROID_SDK_ROOT = $sdkDir
            }

    Write-Host '==> Waiting for the emulator to boot' -ForegroundColor Cyan
    & $adb wait-for-device
    $deadline = (Get-Date).AddMinutes(6)
    do {
        Start-Sleep -Seconds 5
        $booted = (& $adb shell getprop sys.boot_completed 2>$null) -join ''
        $booted = $booted.Trim()
    } while ($booted -ne '1' -and (Get-Date) -lt $deadline)

    if ($booted -ne '1') { throw 'Emulator did not finish booting.' }
    $serial = Get-ConnectedSerial
}

if (-not $serial) {
    throw 'No Android device or emulator connected. Use -Emulator or attach a device with USB debugging enabled.'
}

Write-Host "==> Target device: $serial" -ForegroundColor Cyan

if ($Uninstall) {
    Write-Host '==> Uninstalling previous build' -ForegroundColor Cyan
    & $adb -s $serial uninstall $packageName | Out-Null
}

Write-Host "==> Installing $Apk" -ForegroundColor Cyan
# --v4 bundles the .idsig next to the APK so the device can stream the install from the
# incremental-v4 descriptor. Not every adb/pm build accepts the flag, so fall back to a
# regular install when it is rejected; the APK itself carries its v2/v3 signatures.
$idsig = "$Apk.idsig"
$installed = $false
if (Test-Path $idsig) {
    & $adb -s $serial install -r --v4 "$Apk" 2>&1 | ForEach-Object { Write-Host "    $_" }
    $installed = $LASTEXITCODE -eq 0
    if (-not $installed) {
        Write-Host '==> --v4 rejected by this device, falling back to a regular install' `
            -ForegroundColor Yellow
    }
}
if (-not $installed) {
    & $adb -s $serial install -r "$Apk"
    if ($LASTEXITCODE -ne 0) { throw 'adb install failed.' }
}

$version = (& $adb -s $serial shell dumpsys package $packageName |
    Select-String -Pattern 'versionName' | Select-Object -First 1)
Write-Host "==> Installed: $($version -replace '\s+', ' ')" -ForegroundColor Green

$providers = (& $adb -s $serial shell cmd appwidget list 2>$null) -join "`n"
if ($providers) {
    Write-Host '==> Widgets known to the launcher:' -ForegroundColor Cyan
    ($providers -split "`n" | Where-Object { $_ -match 'flexi' }) | ForEach-Object { Write-Host "    $_" }
}

if ($Launch) {
    Write-Host '==> Launching the app' -ForegroundColor Cyan
    & $adb -s $serial shell am start -n "$packageName/.MainActivity" | Out-Null
}