@echo off
chcp 65001 >nul
title OPC UA 数据采集服务 - 启动
echo ========================================
echo  OPC UA 数据采集服务 - 启动
echo ========================================
echo.

cd /d "%~dp0"

if not exist "opcua-client-1.0.0.jar" (
    echo [错误] 未找到 target\opcua-client-1.0.0.jar
    echo 请先执行 mvn clean package 进行打包！
    pause
    exit /b 1
)

if not exist "logs" mkdir logs

echo [信息] 正在启动服务...
echo 日志文件: logs\opcua-client.log
echo.

start /B javaw -Xmx512m -Xms256m -jar "opcua-client-1.0.0.jar" > logs\opcua-client.log 2>&1

echo [成功] 服务已启动
pause
