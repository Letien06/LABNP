@echo off
setlocal
call build.cmd || exit /b 1
set ID=%1
if "%ID%"=="" set ID=A
if "%NODE_HOST%"=="" set NODE_HOST=127.0.0.1
if "%REPLICA_HOST%"=="" set REPLICA_HOST=127.0.0.1
if "%REPLICA_PORT%"=="" set REPLICA_PORT=1100
if "%PRIMARY_HOST%"=="" set PRIMARY_HOST=127.0.0.1
if "%PRIMARY_PORT%"=="" set PRIMARY_PORT=1099
if /I "%ID%"=="A" (
  java -cp out lab5rmi.server.NodeServer --id A --host %NODE_HOST% --primary true --port 1099 --service-port 1199 --data data-A --replica-host %REPLICA_HOST% --replica-port %REPLICA_PORT%
) else (
  java -cp out lab5rmi.server.NodeServer --id B --host %NODE_HOST% --primary false --port 1100 --service-port 1200 --data data-B --primary-host %PRIMARY_HOST% --primary-port %PRIMARY_PORT%
)
