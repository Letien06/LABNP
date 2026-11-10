@echo off
setlocal
if "%JAVA_HOME%"=="" (
  echo JAVA_HOME must point to a JDK installation. 1>&2
  exit /b 1
)
cd /d "%~dp0"
"%JAVA_HOME%\bin\java.exe" "-Dmaven.multiModuleProjectDirectory=." -cp ".mvn\wrapper\maven-wrapper.jar" org.apache.maven.wrapper.MavenWrapperMain %*
exit /b %errorlevel%
