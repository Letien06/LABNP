param([string]$HomeUrl = 'http://127.0.0.1:8080/')
$ErrorActionPreference = 'Stop'
. "$PSScriptRoot\common.ps1"
& "$PSScriptRoot\build.ps1"
Push-Location (Split-Path -Parent $PSScriptRoot)
try {
    $tools = Get-LabJavaTools
    $fx = Get-LabJavaFxPath
    & $tools.Java --module-path $fx --add-modules javafx.controls,javafx.fxml,javafx.web,jdk.httpserver "-Dlab4.home=$HomeUrl" -cp target\classes com.ronial.browser.Main
    if ($LASTEXITCODE -ne 0) { throw 'Browser exited with an error.' }
} finally { Pop-Location }
