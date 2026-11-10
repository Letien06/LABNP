$ErrorActionPreference = 'Stop'
python -m py_compile mail_service.py smoke_test.py
python smoke_test.py
Write-Host 'Lab3-Mail checks complete.'
