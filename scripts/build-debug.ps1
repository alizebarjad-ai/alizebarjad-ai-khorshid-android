$ErrorActionPreference = "Stop"

$projectRoot = Split-Path -Parent $PSScriptRoot
Set-Location $projectRoot

Write-Host "Khorshid Member Android - Debug build" -ForegroundColor Cyan
Write-Host "Project: $projectRoot"

$gradleCommand = Get-Command gradle -ErrorAction SilentlyContinue
if ($gradleCommand) {
    & $gradleCommand.Source --no-daemon assembleDebug
} else {
    $gradleCache = Join-Path $env:USERPROFILE ".gradle\wrapper\dists\gradle-8.9-bin"
    $gradleBat = Get-ChildItem -Path $gradleCache -Filter "gradle.bat" -File -Recurse -ErrorAction SilentlyContinue |
        Sort-Object LastWriteTime -Descending |
        Select-Object -First 1

    if (-not $gradleBat) {
        Write-Error "Gradle 8.9 was not found. Open the project in Android Studio, complete Gradle Sync, then run this script again."
    }

    & $gradleBat.FullName --no-daemon assembleDebug
}

$apkPath = Join-Path $projectRoot "app\build\outputs\apk\debug\app-debug.apk"
if (-not (Test-Path $apkPath)) {
    throw "Build command finished but APK was not found at: $apkPath"
}

Write-Host ""
Write-Host "Build successful." -ForegroundColor Green
Write-Host "APK: $apkPath"
Get-FileHash $apkPath -Algorithm SHA256 | Format-List
