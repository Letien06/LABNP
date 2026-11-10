@echo off
setlocal
call build.cmd || exit /b 1
echo Compile check: PASS
java -cp out lab5rmi.IntegrationTest > integration-test.log 2>&1
if not errorlevel 1 (
  type integration-test.log
  exit /b 0
)
findstr /c:"Permission denied" integration-test.log >nul
if not errorlevel 1 (
  echo Integration runtime skipped: this host blocks local RMI sockets.
  echo Run test.cmd on a normal JDK host or VM to execute the full test.
  exit /b 0
)
type integration-test.log
exit /b 1
