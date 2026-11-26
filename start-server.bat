@echo off
set "PATH=%LOCALAPPDATA%\Programs\nodejs;%PATH%"
echo ===================================================
echo [PSG iTech Library] Starting Server on Port 8080...
echo Link: http://localhost:8080/
echo ===================================================
node server.js
pause
