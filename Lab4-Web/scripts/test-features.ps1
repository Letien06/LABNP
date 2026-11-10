$ErrorActionPreference = 'Stop'
& (Join-Path (Split-Path -Parent $PSScriptRoot) 'test.ps1') -Ui
