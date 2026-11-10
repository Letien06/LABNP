$ErrorActionPreference = 'Stop'

function Get-LabJavaTools {
    $candidate = $null
    if ($env:JAVA_HOME -and (Test-Path -LiteralPath (Join-Path $env:JAVA_HOME 'bin\javac.exe'))) {
        $candidate = Join-Path $env:JAVA_HOME 'bin\javac.exe'
    } else {
        $command = Get-Command javac.exe -ErrorAction SilentlyContinue
        if ($command) { $candidate = $command.Source }
        if (!$candidate) {
            $installations = Get-ChildItem (Join-Path $env:USERPROFILE '.jdks') -Directory -ErrorAction SilentlyContinue |
                Where-Object { Test-Path -LiteralPath (Join-Path $_.FullName 'bin\javac.exe') } |
                Sort-Object Name -Descending
            $installation = $installations | Select-Object -First 1
            if ($installation) { $candidate = Join-Path $installation.FullName 'bin\javac.exe' }
        }
    }
    if (!$candidate) { throw 'Install JDK 17+ and set JAVA_HOME, or add its bin folder to PATH.' }
    $versionText = (& $candidate -version 2>&1 | Out-String).Trim()
    if ($LASTEXITCODE -ne 0 -or $versionText -notmatch 'javac (\d+)' -or [int]$Matches[1] -lt 17) {
        throw "JDK 17+ is required. Found: $versionText"
    }
    $binaryDirectory = Split-Path -Parent $candidate
    return [pscustomobject]@{ Javac = $candidate; Java = Join-Path $binaryDirectory 'java.exe' }
}

function Get-LabJavaFxPath {
    if ($env:JAVAFX_HOME -and (Test-Path -LiteralPath (Join-Path $env:JAVAFX_HOME 'lib\javafx.web.jar'))) {
        return (Join-Path $env:JAVAFX_HOME 'lib')
    }
    $repository = Join-Path $env:USERPROFILE '.m2\repository\org\openjfx'
    $jars = @('base', 'graphics', 'controls', 'fxml', 'media', 'web') | ForEach-Object {
        $jar = Join-Path $repository "javafx-$_\21\javafx-$_-21-win.jar"
        if (!(Test-Path -LiteralPath $jar)) {
            throw 'JavaFX 21 is missing. Run .\mvnw.cmd -q package once, or set JAVAFX_HOME to a JavaFX 21 SDK folder.'
        }
        $jar
    }
    return ($jars -join ';')
}
