$ErrorActionPreference = "Stop"

$projectRoot = Split-Path -Parent $PSScriptRoot
Set-Location $projectRoot

Write-Host "Khorshid Member Android - Debug build" -ForegroundColor Cyan
Write-Host "Project: $projectRoot"

$wrapper = Join-Path $projectRoot "gradlew.bat"
if (-not (Test-Path $wrapper)) {
    throw "Gradle Wrapper is missing. Pull the latest main branch from GitHub."
}

& $wrapper --no-daemon testDebugUnitTest
if ($LASTEXITCODE -ne 0) { throw "Unit tests failed." }

& $wrapper --no-daemon assembleDebug
if ($LASTEXITCODE -ne 0) { throw "Debug APK build failed." }

$apkPath = Join-Path $projectRoot "app\build\outputs\apk\debug\app-debug.apk"
if (-not (Test-Path $apkPath)) {
    throw "Build command finished but APK was not found at: $apkPath"
}

Write-Host ""
Write-Host "Build and unit tests successful." -ForegroundColor Green
Write-Host "APK: $apkPath"
Get-FileHash $apkPath -Algorithm SHA256 | Format-List
