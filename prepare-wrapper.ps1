$ErrorActionPreference = 'Stop'
$base = Split-Path -Parent $MyInvocation.MyCommand.Path
$destination = Join-Path $base 'gradle\wrapper\gradle-wrapper.jar'
$url = 'https://services.gradle.org/distributions/gradle-8.9-wrapper.jar'
$checksum = $url + '.sha256'
try {
    Write-Host 'Downloading the official Gradle 8.9 wrapper...'
    Invoke-WebRequest -Uri $url -UseBasicParsing -OutFile $destination
    $expected = ((Invoke-WebRequest -Uri $checksum -UseBasicParsing).Content.ToString().Trim() -split '\s+')[0].ToUpperInvariant()
    $actual = (Get-FileHash -Path $destination -Algorithm SHA256).Hash.ToUpperInvariant()
    if ($expected -ne $actual) { throw 'Gradle wrapper checksum does not match!' }
    Write-Host 'OK: official Gradle wrapper SHA-256 verified.'
} catch {
    Remove-Item -Force -ErrorAction SilentlyContinue $destination
    Write-Error "Could not prepare Gradle wrapper: $_"
    exit 1
}
