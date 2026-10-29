@echo off
setlocal EnableExtensions
set "HERE=%~dp0"
set "JAVA_BIN="

rem 1) A JDK folder next to this file (name starts with "jdk", e.g. jdk or jdk-21.0.5+11)
for /d %%D in ("%HERE%jdk*") do if exist "%%~fD\bin\javaw.exe" set "JAVA_BIN=%%~fD\bin"

rem 2) JAVA_HOME
if not defined JAVA_BIN if defined JAVA_HOME if exist "%JAVA_HOME%\bin\javaw.exe" set "JAVA_BIN=%JAVA_HOME%\bin"

rem 3) PATH
if not defined JAVA_BIN for %%J in (javaw.exe) do if not "%%~$PATH:J"=="" set "JAVA_BIN=%%~dp$PATH:J"

if not defined JAVA_BIN (
  echo JDK 21 was not found.
  echo.
  echo Easiest: double-click setup-jdk.bat ^(downloads Temurin 21 into the jdk folder^).
  echo Or extract a Temurin 21 JDK .zip next to this file ^(see README.txt^).
  echo.
  pause
  exit /b 1
)
echo Using: %JAVA_BIN%\java.exe
"%JAVA_BIN%\java.exe" -version
echo.
"%JAVA_BIN%\java.exe" -Dfile.encoding=UTF-8 -cp "%HERE%classes" MiniJavaEditor %*
echo.
echo ---- the editor has closed ^(exit code %ERRORLEVEL%^) ----
pause
