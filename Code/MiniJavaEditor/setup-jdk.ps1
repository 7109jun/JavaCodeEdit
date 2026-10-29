# Downloads Eclipse Temurin JDK 21 (Windows x64) into the "jdk" folder next to this file.
# Source: Adoptium API (https://api.adoptium.net). The download is checked against its published SHA-256.
$ErrorActionPreference = 'Stop'
$ProgressPreference = 'SilentlyContinue'      # much faster downloads in Windows PowerShell 5.1
[Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12

$here = Split-Path -Parent $MyInvocation.MyCommand.Path
$target = Join-Path $here 'jdk'
if (Test-Path (Join-Path $target 'bin\javaw.exe')) {
    Write-Host 'A JDK is already in the jdk folder. Nothing to do.'
    exit 0
}

$api = 'https://api.adoptium.net/v3/binary/latest/21/ga/windows/x64/jdk/hotspot/normal/eclipse'
Write-Host 'Looking up the download address...'
$req = [System.Net.HttpWebRequest]::Create($api)
$req.AllowAutoRedirect = $false
$resp = $req.GetResponse()
$url = $resp.Headers['Location']
$resp.Close()
if (-not $url) { throw 'Could not find the download address.' }

$zip = Join-Path $env:TEMP 'temurin21-jdk.zip'
Write-Host "Downloading (about 200 MB): $url"
Invoke-WebRequest -Uri $url -OutFile $zip -UseBasicParsing

Write-Host 'Verifying the checksum...'
$c = (Invoke-WebRequest -Uri ($url + '.sha256.txt') -UseBasicParsing).Content
if ($c -is [byte[]]) { $c = [System.Text.Encoding]::UTF8.GetString($c) }
$expected = ($c.Trim() -split '\s+')[0].ToLower()
$actual = (Get-FileHash -Path $zip -Algorithm SHA256).Hash.ToLower()
if ($expected -ne $actual) {
    Remove-Item $zip -Force
    throw 'Checksum mismatch. The download was deleted. Please try again later.'
}

Write-Host 'Extracting (this takes a minute)...'
$tmp = Join-Path $env:TEMP 'temurin21-extract'
if (Test-Path $tmp) { Remove-Item $tmp -Recurse -Force }
Expand-Archive -Path $zip -DestinationPath $tmp -Force
$inner = Get-ChildItem -Path $tmp -Directory | Select-Object -First 1
if (-not $inner -or -not (Test-Path (Join-Path $inner.FullName 'bin\javaw.exe'))) {
    throw 'The downloaded archive did not contain a JDK.'
}
if (Test-Path $target) { Remove-Item $target -Recurse -Force }
Move-Item -Path $inner.FullName -Destination $target
Remove-Item $zip -Force
Remove-Item $tmp -Recurse -Force
Write-Host 'Done. Now double-click run.bat.'
