@REM ----------------------------------------------------------------------------
@REM Licensed to the Apache Software Foundation (ASF) under one
@REM or more contributor license agreements.
@REM ----------------------------------------------------------------------------
@REM Maven Wrapper script for Windows
@REM ----------------------------------------------------------------------------

@echo off
setlocal

set MAVEN_PROJECTBASEDIR=%~dp0
set WRAPPER_JAR="%MAVEN_PROJECTBASEDIR%.mvn\wrapper\maven-wrapper.jar"
set WRAPPER_PROPERTIES="%MAVEN_PROJECTBASEDIR%.mvn\wrapper\maven-wrapper.properties"

@REM If maven-wrapper.jar doesn't exist, download it
if not exist %WRAPPER_JAR% (
    echo Downloading Maven Wrapper...
    for /f "tokens=2 delims==" %%a in ('findstr /r "wrapperUrl" %WRAPPER_PROPERTIES%') do set WRAPPER_URL=%%a
    powershell -Command "Invoke-WebRequest -Uri '%WRAPPER_URL%' -OutFile %WRAPPER_JAR%"
)

@REM Find Java
if defined JAVA_HOME (
    set JAVA_EXE="%JAVA_HOME%\bin\java.exe"
) else (
    set JAVA_EXE="java"
)

%JAVA_EXE% -jar %WRAPPER_JAR% %*
