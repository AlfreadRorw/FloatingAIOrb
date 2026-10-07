@echo off
setlocal
set APP_HOME=%~dp0
set GRADLE_VERSION=8.13
set DIST=%USERPROFILE%\.gradle\alf-wrapper\gradle-%GRADLE_VERSION%
set BIN=%DIST%\bin\gradle.bat
if not exist "%BIN%" (
  echo Bootstrapping Gradle %GRADLE_VERSION%...
  powershell -NoProfile -ExecutionPolicy Bypass -Command "$ProgressPreference='SilentlyContinue'; New-Item -ItemType Directory -Force '%DIST%' | Out-Null; Invoke-WebRequest -UseBasicParsing -Uri 'https://services.gradle.org/distributions/gradle-%GRADLE_VERSION%-bin.zip' -OutFile '%DIST%\gradle.zip'; Expand-Archive -Force '%DIST%\gradle.zip' '%DIST%\unpacked'; Copy-Item -Recurse -Force '%DIST%\unpacked\gradle-%GRADLE_VERSION%\*' '%DIST%\'; Remove-Item -Recurse -Force '%DIST%\unpacked'; Remove-Item -Force '%DIST%\gradle.zip'"
)
call "%BIN%" -p "%APP_HOME%" %*
endlocal
