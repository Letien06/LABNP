# Configure the local Windows MySQL service for Ya-mail.
[CmdletBinding()]
param(
    [ValidateNotNullOrEmpty()][string]$AdminUser = 'root',
    [ValidateRange(1, 65535)][int]$Port = 3306,
    [switch]$MigrateSqlite
)

$ErrorActionPreference = 'Stop'
$pythonCommand = Get-Command python.exe -CommandType Application -All -ErrorAction SilentlyContinue |
    Where-Object { $_.Source -notlike '*\Microsoft\WindowsApps\*' } |
    Select-Object -First 1
if (!$pythonCommand) {
    throw 'Install Python 3.12 or newer and add it to PATH.'
}
$python = $pythonCommand.Source
& $python -c 'import mysql.connector'
if ($LASTEXITCODE -ne 0) {
    throw 'Install MySQL dependencies with python -m pip install -r requirements-mysql.txt.'
}
$adminPassword = Read-Host "MySQL password for $AdminUser" -AsSecureString
$appPassword = Read-Host 'Password for lab3_mail (use its current password if already created)' -AsSecureString
if ($appPassword.Length -eq 0) {
    throw 'Enter a password for lab3_mail.'
}

$adminCredential = [PSCredential]::new($AdminUser, $adminPassword)
$appCredential = [PSCredential]::new('lab3_mail', $appPassword)

$startInfo = [System.Diagnostics.ProcessStartInfo]::new()
$startInfo.FileName = $python
$startInfo.Arguments = 'setup_mysql.py'
$startInfo.WorkingDirectory = $PSScriptRoot
$startInfo.UseShellExecute = $false
$startInfo.RedirectStandardInput = $true

$payload = @{
    admin_user = $AdminUser
    admin_password = $adminCredential.GetNetworkCredential().Password
    app_password = $appCredential.GetNetworkCredential().Password
    port = $Port
    migrate = [bool]$MigrateSqlite
} | ConvertTo-Json -Compress

$process = [System.Diagnostics.Process]::new()
$process.StartInfo = $startInfo
try {
    if (-not $process.Start()) {
        throw 'Could not start Python.'
    }
    try {
        # ASCII framing works with both Windows PowerShell and PowerShell 7.
        $process.StandardInput.WriteLine([Convert]::ToBase64String([Text.Encoding]::UTF8.GetBytes($payload)))
    } finally {
        $payload = $null
        $process.StandardInput.Close()
    }
    $process.WaitForExit()
    if ($process.ExitCode -ne 0) {
        throw 'MySQL setup did not complete. Local launcher settings were not saved.'
    }
} finally {
    $payload = $null
    $adminCredential = $null
    $adminPassword.Dispose()
    $process.Dispose()
}

$localDir = Join-Path $PSScriptRoot '.local'
$null = New-Item -ItemType Directory -Path $localDir -Force
$appCredential | Export-Clixml -LiteralPath (Join-Path $localDir 'mysql-credential.xml')
@{
    host = '127.0.0.1'
    port = $Port
    database = 'lab3_yamail'
} | ConvertTo-Json | Set-Content -LiteralPath (Join-Path $localDir 'mysql-settings.json') -Encoding Ascii
Write-Host 'Saved local Ya-mail launcher settings.'
