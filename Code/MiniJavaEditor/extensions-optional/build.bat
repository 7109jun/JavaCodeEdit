@echo off
setlocal EnableExtensions
cd /d "%~dp0"
set "JBIN="
for /d %%D in ("%~dp0..\jdk*") do if exist "%%~fD\bin\javac.exe" set "JBIN=%%~fD\bin\"
if not defined JBIN if defined JAVA_HOME if exist "%JAVA_HOME%\bin\javac.exe" set "JBIN=%JAVA_HOME%\bin\"
if exist out rmdir /s /q out
mkdir out
"%JBIN%javac" -encoding UTF-8 -cp "..\classes" -d out ClaudeAiPlugin.java || goto fail
"%JBIN%jar" --create --file claude-ai.jar --manifest manifest.txt -C out . || goto fail
rmdir /s /q out
echo Built claude-ai.jar
goto :eof
:fail
echo Build failed. JDK 21 is required (see ..\README.txt).
exit /b 1
