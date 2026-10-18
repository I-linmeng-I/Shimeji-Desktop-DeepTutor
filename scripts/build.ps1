# Shimeji-Desktop-DeepTutor build script
# Requires JDK 25 (Microsoft OpenJDK) + Maven 3.9.x
# Usage: powershell -ExecutionPolicy Bypass -File scripts\build.ps1
# NOTE: keep this file ASCII-only. PowerShell 5.1 reads BOM-less scripts as GBK,
# which corrupts Chinese literals and can break string parsing.
$ErrorActionPreference = "Stop"

$repo = Split-Path $PSScriptRoot -Parent

# Locate Microsoft OpenJDK 25 (exact path first, then highest version)
$jdkHome = $null
$exact = "C:\Program Files\Microsoft\jdk-25.0.4.101-hotspot"
if (Test-Path (Join-Path $exact "bin\javac.exe")) {
    $jdkHome = $exact
}
else {
    $found = Get-ChildItem -Path "C:\Program Files\Microsoft" -Directory -Filter "jdk-25*" -ErrorAction SilentlyContinue |
    Sort-Object Name -Descending | Select-Object -First 1
    if ($found -and (Test-Path (Join-Path $found.FullName "bin\javac.exe"))) {
        $jdkHome = $found.FullName
    }
}
if (-not $jdkHome) { throw "JDK 25 not found. Install it with: winget install Microsoft.OpenJDK.25" }

$env:JAVA_HOME = $jdkHome
$env:Path = "$jdkHome\bin;$env:Path"

# Locate mvn
$mvnCmd = Get-Command mvn -ErrorAction SilentlyContinue
if ($mvnCmd) {
    $mvn = $mvnCmd.Source
}
else {
    $mvn = "C:\Users\linmeng\AppData\Local\Programs\apache-maven-3.9.9\bin\mvn.cmd"
}
if (-not (Test-Path $mvn)) { throw "Maven not found. Install Apache Maven 3.9.x" }

Write-Host "JAVA_HOME = $jdkHome"
Write-Host "Maven     = $mvn"
Write-Host "Project   = $repo"

Push-Location $repo
try {
    & $mvn package -DskipTests -B
    if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
}
finally {
    Pop-Location
}

$jar = Join-Path $repo "target\Shimeji-ee.jar"
if (-not (Test-Path $jar)) {
    throw "Build artifact missing: $jar"
}
Write-Host "Build OK: $jar"

# ---------------------------------------------------------------------------
# Extract the release zip into dist\.
# dist\ contains conf/ img/ lib/ plus Shimeji-ee.exe, so it can be launched by
# double-clicking dist\Shimeji-ee.exe.
# target\ alone CANNOT run: the launcher resolves conf\ and img\ from the
# current working directory, and those folders only exist in dist\ / repo root.
# ---------------------------------------------------------------------------
$zip = Get-ChildItem (Join-Path $repo "target") -Filter "Shimeji-ee_*.zip" -ErrorAction SilentlyContinue |
Sort-Object LastWriteTime -Descending | Select-Object -First 1

if (-not $zip) {
    Write-Warning "Release zip not found, skipping dist generation"
    return
}

$dist = Join-Path $repo "dist"
$stale = $false
if (Test-Path $dist) {
    try {
        Remove-Item $dist -Recurse -Force -ErrorAction Stop
    }
    catch {
        $stale = $true
    }
}
if ($stale) {
    Write-Warning "dist is in use (Shimeji may be running). Skipped extraction - close the pet and rebuild."
}
else {
    Expand-Archive -Path $zip.FullName -DestinationPath $dist -Force
    Write-Host "Extracted to: $dist"
    Write-Host "Double-click to run: $(Join-Path $dist 'Shimeji-ee.exe')"
}
