param([switch]$SkipNetwork)
$ErrorActionPreference = "Stop"
Set-Location (Join-Path $PSScriptRoot "..")
& "$PSScriptRoot/build.ps1"
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
javac --release 17 -encoding UTF-8 -cp out -d out src/test/java/lab1/chat/SmokeTest.java
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
java -cp out lab1.chat.SmokeTest
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
if ($SkipNetwork) {
  & "$PSScriptRoot/test-udp.ps1" -SkipNetwork
  if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
  Write-Host "SKIP network: disabled by -SkipNetwork"
  exit 0
}
& "$PSScriptRoot/test-network.ps1"
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
& "$PSScriptRoot/test-udp.ps1"
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
