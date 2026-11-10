$ErrorActionPreference = 'Stop'
& "$PSScriptRoot\build.ps1"
Push-Location (Split-Path -Parent $PSScriptRoot)
try {
    $jdk = $env:JAVA_HOME
    if (-not $jdk -or -not (Test-Path (Join-Path $jdk 'bin\javac.exe'))) {
        $jdk = @("$env:USERPROFILE\.jdks\ms-21.0.10", "$env:USERPROFILE\.jdks\openjdk-23.0.1", "$env:USERPROFILE\.jdks\jbr-17.0.14") |
            Where-Object { Test-Path (Join-Path $_ 'bin\javac.exe') } | Select-Object -First 1
    }
    $fx = (Get-ChildItem "$env:USERPROFILE\.m2\repository\org\openjfx\*\21\*-win.jar").FullName -join ';'
    & "$jdk\bin\javac.exe" -encoding UTF-8 --module-path $fx --add-modules javafx.controls,javafx.fxml,javafx.web -cp target\classes .qa\BrowserFunctionalTest.java
    if ($LASTEXITCODE -ne 0) { throw 'UI test compilation failed.' }
    $previousJdk = $env:JAVA_HOME
    try {
        $env:JAVA_HOME = $jdk
        python -u .qa\functional_test.py 2>&1 | Tee-Object -FilePath .qa\functional-test.log
        if ($LASTEXITCODE -ne 0) { throw 'Functional test failed. See .qa/functional-test.log.' }
    } finally { $env:JAVA_HOME = $previousJdk }
} finally { Pop-Location }
