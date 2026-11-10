param(
    [switch]$Gmail,
    [switch]$NoServer,
    [switch]$ConfigureGmailRelay,
    [string]$JavaHome = $env:JAVA_HOME,
    [int]$SmtpPort = 2525,
    [int]$PopPort = 8110,
    [int]$AccountPort = 8033
)
$ErrorActionPreference = 'Stop'
if ($ConfigureGmailRelay -and ($Gmail -or $NoServer)) {
    throw '-ConfigureGmailRelay requires starting the local Ya-mail server.'
}
Push-Location $PSScriptRoot
$localServer = $null
$previousAdminToken = $env:YAMAIL_ADMIN_TOKEN
try {
    if (!$JavaHome -or !(Test-Path (Join-Path $JavaHome 'bin/javac.exe'))) {
        $candidate = Get-ChildItem (Join-Path $env:USERPROFILE '.jdks') -Directory -ErrorAction SilentlyContinue |
            Where-Object { Test-Path (Join-Path $_.FullName 'bin/javac.exe') } |
            Sort-Object Name -Descending | Select-Object -First 1
        if ($candidate) { $JavaHome = $candidate.FullName }
    }
    if (!$JavaHome) { throw 'Install JDK 23 or newer and set JAVA_HOME.' }
    $java = Join-Path $JavaHome 'bin/java.exe'
    $javac = Join-Path $JavaHome 'bin/javac.exe'
    $cpFile = 'client/.qa-client-cp.txt'
    $deps = if (Test-Path $cpFile) { (Get-Content $cpFile -Raw).Trim() } else { '' }
    if (!$deps -or @($deps.Split(';') | Where-Object { !(Test-Path -LiteralPath $_) }).Count) {
        if (!(Get-Command mvn -ErrorAction SilentlyContinue)) {
            throw 'Run Maven setup first: mvn -f lib/pom.xml install; mvn -f client/pom.xml dependency:build-classpath -Dmdep.outputFile=.qa-client-cp.txt'
        }
        & mvn -q -f lib/pom.xml install -DskipTests
        if ($LASTEXITCODE) { throw 'Library build failed.' }
        & mvn -q -f client/pom.xml dependency:build-classpath '-Dmdep.outputFile=.qa-client-cp.txt'
        if ($LASTEXITCODE) { throw 'Dependency setup failed.' }
        $deps = (Get-Content $cpFile -Raw).Trim()
    }
    $sources = Get-ChildItem client/src/main/java -Recurse -Filter *.java |
        ForEach-Object { $_.FullName.Substring($PWD.Path.Length + 1) }
    New-Item -ItemType Directory -Force client/target/classes | Out-Null
    & $javac -encoding UTF-8 -cp $deps -d client/target/classes $sources
    if ($LASTEXITCODE) { throw 'Client compilation failed.' }
    if (!$Gmail -and !$NoServer) {
        New-Item -ItemType Directory -Force .qa | Out-Null
        $adminTokenBytes = [byte[]]::new(32)
        $random = [Security.Cryptography.RandomNumberGenerator]::Create()
        try {
            $random.GetBytes($adminTokenBytes)
            $env:YAMAIL_ADMIN_TOKEN = [Convert]::ToBase64String($adminTokenBytes)
        } finally {
            $random.Dispose()
        }
        $previousRelayUser = $env:YAMAIL_RELAY_USER
        $previousRelayPassword = $env:YAMAIL_RELAY_PASSWORD
        try {
            if ($ConfigureGmailRelay) {
                $relayUser = (Read-Host 'Gmail address used to send external mail').Trim()
                if ($relayUser -notmatch '^[^\s@]+@(gmail|googlemail)\.com$') { throw 'Enter a valid Gmail address.' }
                $relaySecret = Read-Host 'Google App Password' -AsSecureString
                if ($relaySecret.Length -eq 0) { throw 'Enter the Google App Password.' }
                try {
                    $env:YAMAIL_RELAY_USER = $relayUser
                    $env:YAMAIL_RELAY_PASSWORD = [Net.NetworkCredential]::new('', $relaySecret).Password
                } finally {
                    $relaySecret.Dispose()
                }
            }
            $localServer = Start-Process -FilePath python -ArgumentList @('-u', 'mail_service.py', 'server', '--smtp-port', $SmtpPort, '--pop3-port', $PopPort, '--account-port', $AccountPort) -WorkingDirectory $PSScriptRoot -WindowStyle Hidden -PassThru -RedirectStandardOutput .qa/local-server.log -RedirectStandardError .qa/local-server-error.log
        } finally {
            $env:YAMAIL_RELAY_USER = $previousRelayUser
            $env:YAMAIL_RELAY_PASSWORD = $previousRelayPassword
        }
        $ready = $false
        $deadline = (Get-Date).AddSeconds(8)
        while (!$localServer.HasExited -and (Get-Date) -lt $deadline) {
            try {
                $health = Invoke-RestMethod -Uri "http://127.0.0.1:$AccountPort/health" -TimeoutSec 1
                if ($health.ok) { $ready = $true; break }
            } catch { Start-Sleep -Milliseconds 200 }
        }
        if (!$ready -or $localServer.HasExited) { throw 'Local mail server could not start. Check .qa/local-server-error.log or use -NoServer for an existing server.' }
    }
    $provider = if ($Gmail) { 'gmail' } else { 'local' }
    Write-Host $(if ($Gmail) { 'Gmail: sign in with your Gmail address and app password.' } else { 'Local demo account: student@localhost / student' })
    & $java '-Dfile.encoding=UTF-8' "-Dlab3.provider=$provider" "-Dlab3.smtpPort=$SmtpPort" "-Dlab3.popPort=$PopPort" "-Dlab3.accountPort=$AccountPort" -cp "client/target/classes;client/src/main/resources;$deps" com.ronial.app.MailClientApplication
    if ($LASTEXITCODE) { throw 'Mail client exited with an error.' }
} finally {
    $env:YAMAIL_ADMIN_TOKEN = $previousAdminToken
    if ($localServer -and !$localServer.HasExited) { Stop-Process -Id $localServer.Id }
    Pop-Location
}
