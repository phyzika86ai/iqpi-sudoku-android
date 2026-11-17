@echo off
setlocal
cd /d "%~dp0"
if exist "gradle\wrapper\gradle-wrapper.jar" (
 echo Gradle wrapper already present.
 exit /b 0
)
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0prepare-wrapper.ps1"
if errorlevel 1 (
 echo Failed to download Gradle wrapper. See README.md
 pause
 exit /b 1
)
echo Gradle wrapper installed.
pause
