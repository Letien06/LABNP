@echo off
setlocal
if exist out rmdir /s /q out
mkdir out
powershell -NoProfile -ExecutionPolicy Bypass -Command "$root=(Get-Location).Path; Get-ChildItem src,tests -Recurse -Filter *.java | %% { $_.FullName.Substring($root.Length+1) } | Set-Content -Encoding ascii sources.txt"
javac -encoding UTF-8 -d out @sources.txt
if errorlevel 1 exit /b 1
echo Build complete.
