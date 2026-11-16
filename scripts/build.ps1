# Build script for Flexi World-Clock Widget.
#
# Builds the release APK, zipaligns it, then signs it with APK Signature Scheme
# v2 + v3 and writes a v4 signature (.idsig). v1 (JAR signing) is deliberately off.
#
# Usage:
#   .\scripts\build.ps1                 # full build + sign + verify
#   .\scripts\build.ps1 -Clean          # wipe intermediates first
#   .\scripts\build.ps1 -Test           # also run the JVM unit tests
#   .\scripts\build.ps1 -NoSign         # only assemble the unsigned APK

[CmdletBinding()]
param(
    [switch]$Clean,
    [switch]$Test,
    [switch]$NoSign
)

$ErrorActionPreference = 'Stop'
$ProgressPreference = 'SilentlyContinue'

$repoRoot = Split-Path -Parent $PSScriptRoot
$appDir = Join-Path $repoRoot 'app'
$sdkDir = if ($env:ANDROID_HOME) { $env:ANDROID_HOME } else { 'C:\P\Android-CLI' }
$buildTools = Join-Path $sdkDir 'build-tools\35.0.0'
$jbrHome = 'C:\P\Android Studio\jbr'

if (-not $env:JAVA_HOME -and (Test-Path $jbrHome)) {
    $env:JAVA_HOME = $jbrHome
}
$env:ANDROID_HOME = $sdkDir
$env:ANDROID_SDK_ROOT = $sdkDir

# Keystore credentials (defaults match signing.ps1).
$storeFile = if ($env:FLEXI_STORE_FILE) { $env:FLEXI_STORE_FILE } else { 'keystore/flexi-release.jks' }
$storePass = if ($env:FLEXI_STORE_PASSWORD) { $env:FLEXI_STORE_PASSWORD } else { 'flexiwidget' }
$keyAlias = if ($env:FLEXI_KEY_ALIAS) { $env:FLEXI_KEY_ALIAS } else { 'flexi' }
$keyPass = if ($env:FLEXI_KEY_PASSWORD) { $env:FLEXI_KEY_PASSWORD } else { 'flexiwidget' }

$keystorePath = if ([System.IO.Path]::IsPathRooted($storeFile)) { $storeFile } else { Join-Path $repoRoot $storeFile }

function Invoke-Step {
    param([string]$Label, [scriptblock]$Action)
    Write-Host "==> $Label" -ForegroundColor Cyan
    & $Action
    if ($LASTEXITCODE -ne 0) {
        throw "$Label failed with exit code $LASTEXITCODE"
    }
}

Write-Host "Flexi World-Clock Widget :: build" -ForegroundColor Green
Write-Host "  SDK        : $sdkDir"
Write-Host "  JAVA_HOME  : $env:JAVA_HOME"
Write-Host "  Build tools: $buildTools"

if ($Clean) {
    Invoke-Step 'Gradle clean' { & (Join-Path $repoRoot 'gradlew.bat') clean --console=plain }
}

Invoke-Step 'Gradle assembleRelease' {
    & (Join-Path $repoRoot 'gradlew.bat') ':app:assembleRelease' --console=plain
}

if ($Test) {
    Invoke-Step 'Gradle unit tests' {
        & (Join-Path $repoRoot 'gradlew.bat') ':app:testReleaseUnitTest' --console=plain
    }
}

$unsignedApk = Join-Path $appDir 'build\outputs\apk\release\app-release-unsigned.apk'
if (-not (Test-Path $unsignedApk)) {
    $candidate = Get-ChildItem (Join-Path $appDir 'build\outputs\apk\release') -Filter '*.apk' |
        Select-Object -First 1
    if (-not $candidate) { throw 'No release APK produced.' }
    $unsignedApk = $candidate.FullName
}

$distDir = Join-Path $repoRoot 'dist'
New-Item -ItemType Directory -Force -Path $distDir | Out-Null
$alignedApk = Join-Path $distDir 'flexi-world-clock-widget-aligned.apk'
$signedApk = Join-Path $distDir 'flexi-world-clock-widget.apk'

if ($NoSign) {
    Copy-Item -Force $unsignedApk (Join-Path $distDir 'flexi-world-clock-widget-unsigned.apk')
    Write-Host "Unsigned APK: $(Join-Path $distDir 'flexi-world-clock-widget-unsigned.apk')" -ForegroundColor Yellow
    return
}

if (-not (Test-Path $keystorePath)) {
    throw "Keystore not found: $keystorePath (see signing.ps1 for the generation command)."
}

Invoke-Step 'zipalign' {
    & (Join-Path $buildTools 'zipalign.exe') -p -f 4 $unsignedApk $alignedApk
}

# apksigner signs from the outermost scheme inward: v4 (idsig) -> v3 -> v2 -> v1.
Invoke-Step 'apksigner (v2 + v3 + v4)' {
    & (Join-Path $buildTools 'apksigner.bat') sign `
        --ks $keystorePath `
        --ks-key-alias $keyAlias `
        --ks-pass "pass:$storePass" `
        --key-pass "pass:$keyPass" `
        --v1-signing-enabled false `
        --v2-signing-enabled true `
        --v3-signing-enabled true `
        --v4-signing-enabled true `
        --out $signedApk `
        $alignedApk
}

# Verify against the API 24+ range: from API 31 on only v3 is used, so the default
# (manifest minSdk = 31) check would report v2 as unused. Note the CLI never reports v4 -
# that needs the library API, which the next step uses.
Invoke-Step 'apksigner verify (v2 + v3)' {
    & (Join-Path $buildTools 'apksigner.bat') verify --verbose --print-certs `
        --min-sdk-version 24 $signedApk
}

$idsig = "$signedApk.idsig"
if (Test-Path $idsig) {
    Invoke-Step 'v4 signature verify' {
        $verifierDir = Join-Path $env:TEMP 'flexi-v4-verify'
        New-Item -ItemType Directory -Force -Path $verifierDir | Out-Null
        $toolJar = Join-Path $buildTools 'lib\apksigner.jar'
        & (Join-Path $env:JAVA_HOME 'bin\javac.exe') -nowarn `
            -cp $toolJar -d $verifierDir (Join-Path $PSScriptRoot 'V4Verifier.java')
        if ($LASTEXITCODE -ne 0) { throw 'Could not compile V4Verifier.' }
        & (Join-Path $env:JAVA_HOME 'bin\java.exe') -cp "$verifierDir;$toolJar" `
            V4Verifier $signedApk $idsig
    }
} else {
    Write-Warning 'No .idsig produced, so the APK has no v4 signature.'
}

Remove-Item -Force $alignedApk -ErrorAction SilentlyContinue

Write-Host ''
Write-Host "Signed APK : $signedApk" -ForegroundColor Green
if (Test-Path $idsig) {
    Write-Host "v4 signature: $idsig" -ForegroundColor Green
}
Write-Host "Size       : $([math]::Round((Get-Item $signedApk).Length / 1KB, 1)) KB" -ForegroundColor Green