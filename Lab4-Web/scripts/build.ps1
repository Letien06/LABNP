$ErrorActionPreference = 'Stop'
Push-Location (Split-Path -Parent $PSScriptRoot)
try {
    $jdk = $env:JAVA_HOME
    if (-not $jdk -or -not (Test-Path (Join-Path $jdk 'bin\javac.exe'))) {
        $jdk = @("$env:USERPROFILE\.jdks\ms-21.0.10", "$env:USERPROFILE\.jdks\openjdk-23.0.1", "$env:USERPROFILE\.jdks\jbr-17.0.14") |
            Where-Object { Test-Path (Join-Path $_ 'bin\javac.exe') } | Select-Object -First 1
    }
    if (-not $jdk) { throw 'Set JAVA_HOME to an installed JDK 17 or newer.' }
    $fx = (Get-ChildItem "$env:USERPROFILE\.m2\repository\org\openjfx\*\21\*-win.jar").FullName -join ';'
    if (-not $fx) { throw 'JavaFX 21 is missing. Run mvnw.cmd package once to download the declared dependencies.' }
    New-Item target\classes -ItemType Directory -Force | Out-Null
    $projectRoot = (Get-Location).Path
    $sources = Get-ChildItem src\main\java -Filter '*.java' -Recurse | ForEach-Object { '.\' + $_.FullName.Substring($projectRoot.Length + 1) }
    & "$jdk\bin\javac.exe" -encoding UTF-8 --module-path $fx -d target\classes $sources
    if ($LASTEXITCODE -ne 0) { throw 'Java compilation failed.' }
    Copy-Item src\main\resources\* target\classes -Force
    Write-Output 'Lab4 JavaFX build PASS'
} finally { Pop-Location }
