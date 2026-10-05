@echo off
setlocal
set GRADLE_VERSION=8.9
set CACHE=%USERPROFILE%\.gradle\wrapper\dists\gradle-%GRADLE_VERSION%-bin
set DIST=%CACHE%\gradle-%GRADLE_VERSION%
if not exist "%DIST%\bin\gradle.bat" (
  if not exist "%CACHE%" mkdir "%CACHE%"
  powershell -NoProfile -Command "(New-Object Net.WebClient).DownloadFile('https://services.gradle.org/distributions/gradle-%GRADLE_VERSION%-bin.zip','%CACHE%\gradle.zip')"
  powershell -NoProfile -Command "Expand-Archive -Force '%CACHE%\gradle.zip' '%CACHE%'"
)
call "%DIST%\bin\gradle.bat" %*
