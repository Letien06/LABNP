@echo off
setlocal
call build.cmd || exit /b 1
set HOST=%1
if "%HOST%"=="" set HOST=127.0.0.1
set PORT=%2
if "%PORT%"=="" set PORT=1099
if /I "%3"=="--cli" goto cli
java -cp out lab5rmi.client.RecordDashboard %HOST% %PORT%
exit /b %errorlevel%
:cli
java -cp out lab5rmi.client.RecordClient %HOST% %PORT%
