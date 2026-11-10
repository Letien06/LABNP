param(
  [int]$Port=5555,
  [int]$UdpPort=5556,
  [string]$Data="data",
  [string]$Multicast="239.255.42.99",
  [string]$Broadcast="255.255.255.255",
  [switch]$NoUi
)
$ErrorActionPreference="Stop"
Set-Location (Join-Path $PSScriptRoot "..")
$serverClass=Join-Path (Get-Location) "out/lab1/chat/ServerDashboardMain.class"
$sources=Get-ChildItem "src/main/java" -Recurse -Filter *.java
if (-not (Test-Path -LiteralPath $serverClass) -or ($sources | Where-Object { $_.LastWriteTime -gt (Get-Item -LiteralPath $serverClass).LastWriteTime })) {
  & "$PSScriptRoot/build.ps1"
  if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
}
$entryPoint=if($NoUi){"lab1.chat.ChatServer"}else{"lab1.chat.ServerDashboardMain"}
java -cp out $entryPoint --port $Port --udp-port $UdpPort --data $Data --multicast $Multicast --broadcast $Broadcast
