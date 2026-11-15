param([switch]$MySql)
$ErrorActionPreference = 'Stop'
Push-Location $PSScriptRoot
try {
    & python -B -m unittest discover -s . -p test_mailbox_cache.py
    if ($LASTEXITCODE) { throw 'UIDL cache checks failed.' }
    & python -B .qa/feature_test.py
    if ($LASTEXITCODE) { throw 'SMTP/POP3 protocol checks failed.' }
    if ($MySql) { & python -B test_mailbox_state.py --mysql }
    else { & python -B test_mailbox_state.py }
    if ($LASTEXITCODE) { throw 'Mailbox storage checks failed.' }
    Write-Host 'Lab3-Mail checks PASS.'
} finally { Pop-Location }
