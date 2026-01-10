# build-and-install.ps1
# Usage examples:
#  .\build-and-install.ps1 -Configuration debug
#  .\build-and-install.ps1 -Configuration release                      # use debug keystore (default)
#  .\build-and-install.ps1 -Configuration release -KeystorePath C:\keys\mykeystore.jks -KeystorePassword mypass -KeyAlias myalias -KeyPassword keypass

param(
    [ValidateSet('debug','release')]
    [string]$Configuration = 'debug',
    [switch]$Install,
    [string]$DeviceSerial,
    # Optional custom keystore parameters for signing release APK
    [string]$KeystorePath,
    [string]$KeystorePassword,
    [string]$KeyAlias = 'androiddebugkey',
    [string]$KeyPassword
)

$root = Split-Path -Parent $MyInvocation.MyCommand.Definition
Set-Location $root

$adb = "$env:USERPROFILE\AppData\Local\Android\Sdk\platform-tools\adb.exe"
if (-not (Test-Path $adb)) {
    Write-Error "adb not found at $adb. Make sure Android platform-tools are installed or add adb to PATH."; exit 1
}

if ($Configuration -eq 'debug') {
    Write-Output "Building debug APK..."
    .\gradlew assembleDebug | Write-Output
    $apk = Join-Path $root 'app\build\outputs\apk\debug\app-debug.apk'
} else {
    Write-Output "Building release APK (unsigned)..."
    .\gradlew assembleRelease | Write-Output
    $unsigned = Join-Path $root 'app\build\outputs\apk\release\app-release-unsigned.apk'
    if (-not (Test-Path $unsigned)) { Write-Error "Unsigned release APK not found: $unsigned"; exit 1 }

    # Determine keystore to use: custom provided or default debug keystore
    # locate build-tools tools (apksigner/zipalign)
    $buildToolsDir = Get-ChildItem "$env:LOCALAPPDATA\Android\Sdk\build-tools" | Sort-Object Name -Descending | Select-Object -First 1 | ForEach-Object { $_.FullName }
    $apksigner = Join-Path $buildToolsDir 'apksigner.bat'
    if (-not (Test-Path $apksigner)) { Write-Error "apksigner not found in $buildToolsDir"; exit 1 }

    $distDir = Join-Path $root 'dist'
    New-Item -ItemType Directory -Force -Path $distDir | Out-Null
    $signed = Join-Path $distDir 'app-release-signed.apk'

    if ($KeystorePath) {
        if (-not (Test-Path $KeystorePath)) { Write-Error "Provided keystore path not found: $KeystorePath"; exit 1 }
        $keystore = $KeystorePath
        if (-not $KeystorePassword) {
            # prompt for keystore password (visible input) if not provided
            $KeystorePassword = Read-Host -Prompt "Enter keystore password (will be visible)"
        }
        if (-not $KeyPassword) { $KeyPassword = $KeystorePassword }
    } else {
        $keystore = Join-Path $env:USERPROFILE '.android\debug.keystore'
        if (-not (Test-Path $keystore)) { Write-Error "debug keystore not found: $keystore"; exit 1 }
        # default debug keystore credentials
        if (-not $KeystorePassword) { $KeystorePassword = 'android' }
        if (-not $KeyPassword) { $KeyPassword = 'android' }
        # default alias already set via param ($KeyAlias)
    }

    $ksPassArg = "pass:$KeystorePassword"
    $keyPassArg = "pass:$KeyPassword"

    & $apksigner sign --ks $keystore --ks-key-alias $KeyAlias --ks-pass $ksPassArg --key-pass $keyPassArg --out $signed $unsigned
    if ($LASTEXITCODE -ne 0) { Write-Error "Signing failed"; exit 1 }

    # zipalign (optional)
    $zipalign = Join-Path $buildToolsDir 'zipalign.exe'
    if (Test-Path $zipalign) {
        $aligned = Join-Path $distDir 'app-release-signed-aligned.apk'
        & $zipalign -v -p 4 $signed $aligned
        if ($LASTEXITCODE -eq 0) { $apk = $aligned } else { $apk = $signed }
    } else {
        $apk = $signed
    }
}

if (-not (Test-Path $apk)) { Write-Error "APK not found: $apk"; exit 1 }
Write-Output "Built APK: $apk"

if ($Install) {
    # list devices
    $devices = & $adb devices | Select-String "\tdevice$" | ForEach-Object { $_.ToString().Split("`t")[0] }
    if ($devices.Count -eq 0) { Write-Error "No adb devices found"; exit 1 }
    if ([string]::IsNullOrEmpty($DeviceSerial)) {
        if ($devices.Count -gt 1) {
            Write-Output "Multiple devices found. Pick one or pass -DeviceSerial <serial>."; $devices | ForEach-Object { Write-Output $_ }
            exit 1
        } else {
            $DeviceSerial = $devices[0]
        }
    }

    Write-Output "Installing $apk to $DeviceSerial"
    & $adb -s $DeviceSerial install -r $apk
    if ($LASTEXITCODE -ne 0) { Write-Error "adb install failed"; exit 1 }
    Write-Output "Install finished successfully"
}

Write-Output "Done."
