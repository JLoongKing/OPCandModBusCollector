@echo off
title OPC UA Data Collection Service - Stop
echo ========================================
echo  OPC UA Data Collection Service - Stop
echo ========================================
echo.

cd /d "%~dp0"

echo [INFO] Looking for service process...

for /f "tokens=5 delims= " %%a in ('netstat -ano ^| findstr :8080 ^| findstr LISTENING 2^>nul') do (
    set PID=%%a
)

if defined PID (
    echo [INFO] Found process PID: %PID%
    echo [INFO] Stopping service...
    taskkill /F /PID %PID% >nul 2>nul
    
    if %errorlevel% equ 0 (
        echo [OK] Service stopped
    ) else (
        echo [WARN] Failed to stop, please manually kill PID: %PID%
    )
) else (
    echo [INFO] No running service found (port 8080 not in use)
)

echo.
pause
