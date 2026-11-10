$ErrorActionPreference = 'Stop'
Set-Location (Join-Path $PSScriptRoot '..')
$qa = '.qa/network-classes'
New-Item -ItemType Directory -Force $qa | Out-Null
$sources = @(Get-ChildItem -Path 'src/main/java','src/test/java' -Recurse -Filter '*.java' | ForEach-Object { $_.FullName.Substring((Get-Location).Path.Length + 1) })
& javac --release 17 -encoding UTF-8 -d $qa $sources
if ($LASTEXITCODE -ne 0) { throw 'javac failed' }
& java -cp $qa lab1.chat.NetworkIntegrationTest
$code = $LASTEXITCODE
if ($code -ne 0) { exit $code }
