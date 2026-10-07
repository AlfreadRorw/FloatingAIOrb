@echo off
set DIR=%~dp0
if defined JAVA_HOME set JAVAEXE=%JAVA_HOME%\bin\java.exe
if not defined JAVAEXE set JAVAEXE=java.exe
"%JAVAEXE%" -Xmx64m -Xms64m -Dorg.gradle.appname=gradlew -classpath "%DIR%gradle\wrapper\gradle-wrapper.jar" org.gradle.wrapper.GradleWrapperMain %*
