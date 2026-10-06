@echo off
where gradle >nul 2>nul
if %ERRORLEVEL% EQU 0 (
    gradle %*
    exit /b %ERRORLEVEL%
)
echo ERROR: Gradle tidak ditemukan di PATH.
exit /b 1
