@echo off
set "PATH=%LOCALAPPDATA%\Programs\nodejs;%PATH%"
echo ========================================================
echo [PSG iTech Library] Creating Public Internet Tunnel...
echo Forwarding port 8080 to the web via localtunnel...
echo ========================================================
npx localtunnel --port 8080
pause
