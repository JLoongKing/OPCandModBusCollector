@echo off
title OPC UA Data Collection Service - Start
echo ========================================
echo  OPC UA Data Collection Service - Start
echo ========================================
echo.

cd /d "%~dp0"

if not exist "target\opcua-client-1.0.0.jar" (
    echo [ERROR] target\opcua-client-1.0.0.jar not found
    echo Please run 'mvn clean package' first!
    pause
    exit /b 1
)

if not exist "logs" mkdir logs

echo [INFO] Starting service...
echo Log file: logs\opcua-client.log
echo.

start /B javaw -Xmx512m -Xms256m -jar "target\opcua-client-1.0.0.jar" > logs\opcua-client.log 2>&1

echo [OK] Service started
pause
