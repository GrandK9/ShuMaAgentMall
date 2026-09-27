# Publish a local YAML file to the Nacos config center.
#
# Usage:
#   powershell -ExecutionPolicy Bypass -File nacos-config\publish.ps1 `
#       -Source tmp\nacos-ns-common.yaml -DataId shumamall-common.yaml
#
# The repository only ships templates (nacos-config\*.yaml) whose secrets are
# placeholders. Keep the filled-in copy outside version control (e.g. under tmp\),
# so JWT / AES / API keys never reach the git history.
#
# Why curl.exe instead of Invoke-RestMethod or a hand-built form body:
# these configs contain Chinese text. Invoke-RestMethod mangles the encoding, and a
# hand-built body with Uri.EscapeDataString ended up sending blank content
# ("content is blank"). curl.exe --data-urlencode does the right thing.
#
# NOTE: keep this file ASCII-only. Windows PowerShell 5.1 decodes BOM-less .ps1 as ANSI/GBK.
param(
    [Parameter(Mandatory = $true)][string]$Source,
    [Parameter(Mandatory = $true)][string]$DataId,
    [string]$Namespace = 'shumamall',
    [string]$Group = 'DEFAULT_GROUP',
    [string]$ServerAddr = 'http://localhost:8848'
)

if (-not (Test-Path $Source)) { throw "missing $Source" }

$url = "$ServerAddr/nacos/v1/cs/configs"
$result = & curl.exe -s -X POST $url `
    --data-urlencode "dataId=$DataId" `
    --data-urlencode "group=$Group" `
    --data-urlencode "tenant=$Namespace" `
    --data-urlencode "type=yaml" `
    "--data-urlencode" "content@$Source"

Write-Output "publish result: $result"

$len = (Get-Item $Source).Length
# curl output arrives as a string array (one element per line); join before measuring,
# otherwise .Length returns the line count instead of the byte count.
$readBack = (& curl.exe -s "$url`?dataId=$DataId&group=$Group&tenant=$Namespace") -join "`n"
Write-Output ("published bytes={0}  readback bytes={1}" -f $len, $readBack.Length)
