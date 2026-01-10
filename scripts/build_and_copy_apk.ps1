# Build debug APK and copy to dist/ for easy sharing
Set-Location $PSScriptRoot\..  # project root
Write-Host "Running Gradle assembleDebug..."
./gradlew assembleDebug
$apk = "app\build\outputs\apk\debug\app-debug.apk"
if (Test-Path $apk) {
    New-Item -Path dist -ItemType Directory -Force | Out-Null
    Copy-Item $apk -Destination dist\ -Force
    Write-Host "APK copied to dist\app-debug.apk"
} else {
    Write-Error "APK not found at $apk"
}

