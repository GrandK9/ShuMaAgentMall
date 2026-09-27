# Fetch and unpack the Elasticsearch IK Chinese tokenizer into es-plugins\ik.
#
# es-plugins\ik holds the unpacked official IK release (6 jars + dictionaries,
# ~9.5MB in total, 8MB of which are the dictionaries). That is third-party
# binary rather than project source, so it is not tracked by git (see
# .gitignore). Run this script once after cloning, before starting the
# elasticsearch container -- an empty mounted plugin dir makes ES refuse to start.
#
# Idempotent: skips when plugin-descriptor.properties already exists, pass
# -Force to re-download. Download source is the INFINI Labs distribution
# endpoint (IK is maintained by INFINI now, classes live in com.infinilabs).
#
# Usage:
#   powershell -ExecutionPolicy Bypass -File es-plugins\fetch-ik.ps1
#   powershell -ExecutionPolicy Bypass -File es-plugins\fetch-ik.ps1 -Destination tmp\ik-verify
#
# NOTE: keep this file ASCII-only. Windows PowerShell 5.1 decodes BOM-less .ps1
# as ANSI/GBK, which corrupts string literals containing Chinese characters.
[CmdletBinding()]
param(
    [string]$Destination = (Join-Path $PSScriptRoot 'ik'),
    [string]$Version = '8.18.8',
    [switch]$Force
)

$ErrorActionPreference = 'Stop'

# Must match the elasticsearch image tag in docker-compose.yml, otherwise ES
# refuses to load the plugin (it checks the version on startup).
$version = $Version
$url = "https://get.infini.cloud/elasticsearch/analysis-ik/$version"
# Alternative source (GitHub release, may time out from mainland China):
#   https://github.com/infinilabs/analysis-ik/releases/download/v$version/elasticsearch-analysis-ik-$version.zip

$marker = Join-Path $Destination 'plugin-descriptor.properties'
if ((Test-Path $marker) -and -not $Force) {
    Write-Host "IK plugin already present, skipping download: $Destination (use -Force to re-download)"
    exit 0
}

$tmpZip = Join-Path ([System.IO.Path]::GetTempPath()) "analysis-ik-$version.zip"
Write-Host "Downloading IK plugin $version"
Write-Host "  $url"
Invoke-WebRequest -Uri $url -OutFile $tmpZip -UseBasicParsing

if (-not (Test-Path $Destination)) {
    New-Item -ItemType Directory -Path $Destination -Force | Out-Null
}
Write-Host "Extracting to $Destination"
Expand-Archive -Path $tmpZip -DestinationPath $Destination -Force
Remove-Item $tmpZip -Force

# Verify key files: a missing one makes ES fail to start with "not a valid plugin".
$required = @(
    'plugin-descriptor.properties',
    "elasticsearch-analysis-ik-$version.jar",
    'ik-core-1.0.jar',
    'config\main.dic',
    'config\IKAnalyzer.cfg.xml'
)
foreach ($f in $required) {
    if (-not (Test-Path (Join-Path $Destination $f))) {
        throw "missing $f after extraction, the downloaded package is incomplete"
    }
}

Write-Host 'Done. Recreate the ES container to load the plugin: docker compose up -d --force-recreate elasticsearch'
