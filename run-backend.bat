@echo off
title SIS Backend - Spring Boot (Port 8085)
color 0E

echo ======================================================================
echo    STARTING SIS BACKEND (SPRING BOOT REST & SSE SERVER)
echo ======================================================================
echo.

cd /d "%~dp0"

echo Current Directory: %CD%
echo Starting Spring Boot on http://localhost:8085 ...
echo MongoDB Atlas + Upstash Redis connected.
echo.

if exist "target\sis-backend-0.0.1-SNAPSHOT.jar" (
    echo Launching precompiled production JAR...
    java -jar target\sis-backend-0.0.1-SNAPSHOT.jar
) else (
    echo Compiling and running with Maven wrapper...
    .\mvnw.cmd spring-boot:run
)

pause
