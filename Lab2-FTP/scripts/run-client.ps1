param([Alias('Host')][string]$ServerHost='127.0.0.1',[int]$Port=2121,[switch]$Cli,[switch]$NoUi)
Set-Location $PSScriptRoot\..
if (Get-Command mvn -ErrorAction SilentlyContinue) { mvn -q -DskipTests package } else { & "$PSScriptRoot\build.ps1" }
if ($Cli -or $NoUi) { java -cp target\classes edu.vku.ftp.FtpClient $ServerHost $Port } else { java -cp target\classes edu.vku.ftp.FtpClientFrame $ServerHost $Port }
