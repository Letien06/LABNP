$ErrorActionPreference = 'Stop'
Push-Location (Split-Path $PSScriptRoot -Parent)
try {
    New-Item -ItemType Directory -Force -Path '.qa\classes', 'out' | Out-Null
    $sources = Get-ChildItem src,tests -Recurse -Filter '*.java' | ForEach-Object { Resolve-Path -Relative $_.FullName }
    & javac -encoding UTF-8 -d '.qa\classes' $sources
    if ($LASTEXITCODE -ne 0) { throw 'Source compilation failed' }
    & javac -encoding UTF-8 -cp '.qa\classes' -d '.qa\classes' '.qa\FunctionalVerification.java'
    if ($LASTEXITCODE -ne 0) { throw 'Feature harness compilation failed' }
    & java -cp '.qa\classes' lab5rmi.client.FunctionalVerification 2>&1 | Tee-Object '.qa\features-after-fix.log'
    if ($LASTEXITCODE -ne 0) { throw 'Feature verification failed; see features-after-fix.log' }
    & java -cp '.qa\classes' lab5rmi.IntegrationTest 2>&1 | Tee-Object '.qa\integration-features.log'
    if ($LASTEXITCODE -ne 0) { throw 'Integration test failed; see integration-features.log' }
} finally {
    Pop-Location
}
