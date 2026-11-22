@echo off
rem DayTrail setup wizard entry point (double-click to run).
rem Extra arguments are passed through to the PowerShell script, for AI / automation.
cd /d "%~dp0"
chcp 65001 >nul
where pwsh >nul 2>nul
if errorlevel 1 goto use_windows_powershell
pwsh -NoProfile -ExecutionPolicy Bypass -File "scripts\setup-my-app.ps1" %*
goto done
:use_windows_powershell
powershell -NoProfile -ExecutionPolicy Bypass -File "scripts\setup-my-app.ps1" %*
:done
set EXITCODE=%ERRORLEVEL%
if "%~1"=="" pause
exit /b %EXITCODE%
