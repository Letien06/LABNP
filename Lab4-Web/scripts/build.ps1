$ErrorActionPreference = 'Stop'
. "$PSScriptRoot\common.ps1"
Push-Location (Split-Path -Parent $PSScriptRoot)
try {
    $tools = Get-LabJavaTools
    $fx = Get-LabJavaFxPath
    New-Item target\classes -ItemType Directory -Force | Out-Null
    $projectRoot = (Get-Location).Path
    $sources = Get-ChildItem src\main\java -Filter '*.java' -Recurse |
        ForEach-Object { '.\' + $_.FullName.Substring($projectRoot.Length + 1) }
    & $tools.Javac --release 17 -encoding UTF-8 --module-path $fx -d target\classes $sources
    if ($LASTEXITCODE -ne 0) { throw 'Java compilation failed.' }
    Copy-Item src\main\resources\* target\classes -Recurse -Force
    Write-Output 'Lab4 JavaFX + Java HTTP build PASS'
} finally { Pop-Location }
