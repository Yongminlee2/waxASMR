@echo off
rem The user home path contains non-ASCII characters. Gradle writes the test
rem worker classpath argfile as UTF-8 while java.exe reads it as the system
rem codepage, which corrupts the classpath. Use an ASCII Gradle home instead.
set GRADLE_USER_HOME=C:\gradle-home
call "%~dp0gradlew.bat" -p "%~dp0." :app:assembleDebug %*
