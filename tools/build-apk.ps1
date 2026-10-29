# Build signed APK (Windows PowerShell)
# Usage: powershell -ExecutionPolicy Bypass -File tools\build-apk.ps1
# Tries --offline first (uses the local dependency cache only, no download traffic),
# and falls back to an online build if the offline cache is incomplete.
$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent (Split-Path -Parent $MyInvocation.MyCommand.Path)
Set-Location $root

if (-not (Test-Path "app\wifivault-release.jks")) {
    throw "Missing app\wifivault-release.jks. Self-signed updates must reuse the same signing key; restore it from backup instead of generating a new one."
}

& .\gradlew.bat --console=plain --offline assembleRelease
if ($LASTEXITCODE -ne 0) {
    Write-Output "Offline build unavailable, retrying online..."
    & .\gradlew.bat --console=plain assembleRelease
    if ($LASTEXITCODE -ne 0) { throw "Build failed" }
}

$metaPath = "app\build\outputs\apk\release\output-metadata.json"
if (-not (Test-Path $metaPath)) { throw "Missing $metaPath" }
$meta = Get-Content $metaPath -Raw | ConvertFrom-Json
$versionName = $meta.elements[0].versionName
$outputFile = $meta.elements[0].outputFile
$sourceApk = Join-Path "app\build\outputs\apk\release" $outputFile
$targetApk = "WiFiVault-$versionName.apk"
Copy-Item $sourceApk $targetApk -Force
Write-Output "Done: $root\$targetApk"
