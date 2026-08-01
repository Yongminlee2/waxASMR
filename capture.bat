@echo off
rem Grabs the phone screen into docs\store-assets\phone\.
rem Usage:  capture.bat 01-home     -> saves 01-home.png
rem The emulator cannot run this app (arm64-only native libs), so it always
rem targets the USB phone.
setlocal

set SERIAL=R3CN50JXF9E
set ADB=%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe
set OUT=%~dp0docs\store-assets\phone

if "%~1"=="" (
    echo   usage: capture.bat ^<name^>
    echo   example: capture.bat 01-home
    exit /b 1
)

if not exist "%OUT%" mkdir "%OUT%"

"%ADB%" -s %SERIAL% exec-out screencap -p > "%OUT%\%~1.png"
if errorlevel 1 (
    echo   capture failed - is the phone connected and unlocked?
    exit /b 1
)

echo   saved: %OUT%\%~1.png
