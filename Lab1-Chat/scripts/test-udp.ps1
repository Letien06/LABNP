param([switch]$SkipNetwork)
$ErrorActionPreference = 'Stop'
Set-Location -LiteralPath (Join-Path $PSScriptRoot '..')
New-Item -ItemType Directory -Force -Path 'out' | Out-Null
# Compile the independent UDP backend and its test without launching any UI.
javac --release 17 -encoding UTF-8 -d out src/main/java/lab1/chat/UdpChatChannel.java src/test/java/lab1/chat/UdpChannelTest.java
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
if ($SkipNetwork) {
    java -cp out lab1.chat.UdpChannelTest --offline
} else {
    java -cp out lab1.chat.UdpChannelTest
}
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
