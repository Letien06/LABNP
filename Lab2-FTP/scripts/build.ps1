Set-Location $PSScriptRoot\..
New-Item -ItemType Directory -Force target\classes | Out-Null
Get-ChildItem -Recurse src\main\java -Filter *.java | ForEach-Object { $_.FullName.Substring((Get-Location).Path.Length+1) } | Set-Content -Encoding ASCII target\sources.txt
javac -encoding UTF-8 --release 17 -d target\classes @target\sources.txt
if ($LASTEXITCODE -ne 0) { throw 'javac failed' }
