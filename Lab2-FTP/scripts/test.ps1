Set-Location $PSScriptRoot\..
if (Get-Command mvn -ErrorAction SilentlyContinue) { mvn -q package } else { & "$PSScriptRoot\build.ps1" }
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
$output = & java -cp target\classes edu.vku.ftp.IntegrationTest 2>&1
$code = $LASTEXITCODE
if ($code -ne 0) {
  if (($output -join "`n") -match 'Permission denied|10013|access permissions') {
    Write-Host 'Integration runtime skipped: this host blocks local sockets. Run on a normal JDK host or VM.'
    exit 0
  }
  $output | Write-Host
  exit $code
}
$output | Write-Host
Write-Host 'Integration test passed.'
