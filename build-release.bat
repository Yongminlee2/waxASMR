@echo off
rem Builds the signed release bundle (AAB) for the Play Store.
rem Requires keystore.properties and the .jks file. See docs/RELEASE.md.
rem
rem The user home path contains non-ASCII characters. Gradle writes the test
rem worker classpath argfile as UTF-8 while java.exe reads it as the system
rem codepage, which corrupts the classpath. Use an ASCII Gradle home instead.
set GRADLE_USER_HOME=C:\gradle-home

if not exist "%~dp0keystore.properties" (
    echo.
    echo   keystore.properties not found - the bundle would be unsigned.
    echo   See docs/RELEASE.md step 1.
    echo.
    exit /b 1
)

call "%~dp0gradlew.bat" -p "%~dp0." :app:testDebugUnitTest :app:bundleRelease %*
if errorlevel 1 exit /b 1

echo.
echo   Done: app\build\outputs\bundle\release\app-release.aab
echo.
