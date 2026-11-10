$ErrorActionPreference = 'Stop'
python -m py_compile web_service.py smoke_test.py
python smoke_test.py
Write-Host 'Lab4-Web checks complete.'
