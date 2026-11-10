param([string]$HomeUrl = 'http://127.0.0.1:8080/')
$ErrorActionPreference = 'Stop'
& "$PSScriptRoot\build.ps1"
Push-Location (Split-Path -Parent $PSScriptRoot)
try {
    $jdk = $env:JAVA_HOME
    if (-not $jdk -or -not (Test-Path (Join-Path $jdk 'bin\java.exe'))) {
        $jdk = @("$env:USERPROFILE\.jdks\ms-21.0.10", "$env:USERPROFILE\.jdks\openjdk-23.0.1", "$env:USERPROFILE\.jdks\jbr-17.0.14") |
            Where-Object { Test-Path (Join-Path $_ 'bin\java.exe') } | Select-Object -First 1
    }
    $fx = (Get-ChildItem "$env:USERPROFILE\.m2\repository\org\openjfx\*\21\*-win.jar").FullName -join ';'
    & "$jdk\bin\java.exe" --module-path $fx --add-modules javafx.controls,javafx.fxml,javafx.web "-Dlab4.home=$HomeUrl" -cp target\classes com.ronial.browser.Main
    if ($LASTEXITCODE -ne 0) { throw 'Browser exited with an error.' }
} finally { Pop-Location }
