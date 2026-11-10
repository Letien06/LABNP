param([int]$Port=2121)
Set-Location $PSScriptRoot\..
if (Get-Command mvn -ErrorAction SilentlyContinue) { mvn -q -DskipTests package } else { & "$PSScriptRoot\build.ps1" }
java -cp target\classes edu.vku.ftp.FtpServer $Port
