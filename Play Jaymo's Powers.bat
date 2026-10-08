@echo off
rem Double-click to build Jaymo's Powers and launch the test copy of Minecraft with it (and Jaymo's Kitchen).
title Jaymo's Powers - test game
cd /d "%~dp0"
set "JAVA_HOME=C:\Program Files\Eclipse Adoptium\jdk-25.0.4.101-hotspot"
set "GRADLE_USER_HOME=E:\gradle-home"
echo Starting Minecraft with Jaymo's Powers + Jaymo's Kitchen... (closing this window closes the game)
call gradlew.bat runClient
if errorlevel 1 (
	echo.
	echo Something went wrong - copy the text above and show it to Claude.
	pause
)
