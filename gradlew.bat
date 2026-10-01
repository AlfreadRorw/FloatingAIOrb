@echo off
where gradle >nul 2>nul
if %ERRORLEVEL% EQU 0 (
  gradle %*
  exit /b %ERRORLEVEL%
)
echo Gradle is not installed on this machine. Install Gradle 8.9+ or use Android Studio/GitHub Actions.
exit /b 1
