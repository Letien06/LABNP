param(
  [Alias("Host")][string]$ServerHost="127.0.0.1",
  [int]$Port=5555,
  [int]$UdpPort=5556,
  [string]$Multicast="239.255.42.99",
  [int]$BroadcastPort=5557,
  [string]$Broadcast="255.255.255.255"
)
$ErrorActionPreference="Stop"
Set-Location (Join-Path $PSScriptRoot "..")
$clientClass=Join-Path (Get-Location) "out/lab1/chat/ChatClient.class"
$sources=Get-ChildItem "src/main/java" -Recurse -Filter *.java
if (-not (Test-Path -LiteralPath $clientClass) -or ($sources | Where-Object { $_.LastWriteTime -gt (Get-Item -LiteralPath $clientClass).LastWriteTime })) {
  & "$PSScriptRoot/build.ps1"
  if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
}
java -cp out lab1.chat.ChatClient --host $ServerHost --port $Port --udp-port $UdpPort --multicast $Multicast --broadcast-port $BroadcastPort --broadcast $Broadcast
