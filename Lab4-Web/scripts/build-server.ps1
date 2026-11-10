$ErrorActionPreference = 'Stop'
. "$PSScriptRoot\common.ps1"
Push-Location (Split-Path -Parent $PSScriptRoot)
try {
    $tools = Get-LabJavaTools
    New-Item target\server-classes -ItemType Directory -Force | Out-Null
    $projectRoot = (Get-Location).Path
    $sources = Get-ChildItem src\main\java\com\ronial\browser\model,src\main\java\com\ronial\browser\service,src\main\java\com\ronial\browser\server -Filter '*.java' -Recurse |
        ForEach-Object { '.\' + $_.FullName.Substring($projectRoot.Length + 1) }
    & $tools.Javac --release 17 --add-modules jdk.httpserver -encoding UTF-8 -d target\server-classes $sources
    if ($LASTEXITCODE -ne 0) { throw 'Java HTTP server/service compilation failed.' }
    Write-Output 'Lab4 Java HTTP server/service build PASS'
} finally { Pop-Location }
