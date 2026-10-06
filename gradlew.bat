@echo off
where gradle >nul 2>nul
if %ERRORLEVEL% EQU 0 (gradle %*) else (echo Gradle belum tersedia. Gunakan GitHub Actions & exit /b 1)
