param([switch]$Ui)
$ErrorActionPreference = 'Stop'
. "$PSScriptRoot\scripts\common.ps1"
Push-Location $PSScriptRoot
try {
    & "$PSScriptRoot\scripts\build-server.ps1"
    $tools = Get-LabJavaTools
    New-Item target\test-classes -ItemType Directory -Force | Out-Null
    $projectRoot = (Get-Location).Path
    $sources = Get-ChildItem tests -Filter '*.java' -Recurse |
        Where-Object { $_.Name -ne 'BrowserFunctionalTest.java' } |
        ForEach-Object { '.\' + $_.FullName.Substring($projectRoot.Length + 1) }
    & $tools.Javac --release 17 --add-modules jdk.httpserver -encoding UTF-8 -cp target\server-classes -d target\test-classes $sources
    if ($LASTEXITCODE -ne 0) { throw 'HTTP test compilation failed.' }
    foreach ($suite in @('ServerIntegrationTest', 'HttpServiceIntegrationTest')) {
        & $tools.Java --add-modules jdk.httpserver -cp 'target\server-classes;target\test-classes' "com.ronial.browser.tests.$suite"
        if ($LASTEXITCODE -ne 0) { throw "$suite failed." }
    }
    if ($Ui) {
        & "$PSScriptRoot\scripts\build.ps1"
        $fx = Get-LabJavaFxPath
        & $tools.Javac --release 17 --module-path $fx --add-modules javafx.controls,javafx.fxml,javafx.web,jdk.httpserver -encoding UTF-8 -cp target\classes -d target\test-classes tests\com\ronial\browser\tests\BrowserFunctionalTest.java
        if ($LASTEXITCODE -ne 0) { throw 'JavaFX test compilation failed.' }
        & $tools.Java --module-path $fx --add-modules javafx.controls,javafx.fxml,javafx.web,jdk.httpserver -cp 'target\classes;target\test-classes' com.ronial.browser.tests.BrowserFunctionalTest
        if ($LASTEXITCODE -ne 0) { throw 'JavaFX functional checks failed.' }
    }
    Write-Output 'Lab4 checks PASS (Java only)'
} finally { Pop-Location }
