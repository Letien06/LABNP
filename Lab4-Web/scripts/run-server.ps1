param(
    [string]$BindAddress = '127.0.0.1',
    [int]$Port = 8080,
    [string]$PublicDirectory = 'public',
    [string]$UploadDirectory = 'downloads'
)
$ErrorActionPreference = 'Stop'
. "$PSScriptRoot\common.ps1"
& "$PSScriptRoot\build-server.ps1"
Push-Location (Split-Path -Parent $PSScriptRoot)
try {
    $tools = Get-LabJavaTools
    & $tools.Java --add-modules jdk.httpserver -cp target\server-classes com.ronial.browser.server.ServerMain --host $BindAddress --port $Port --public $PublicDirectory --uploads $UploadDirectory
    if ($LASTEXITCODE -ne 0) { throw 'Java HTTP server exited with an error.' }
} finally { Pop-Location }
