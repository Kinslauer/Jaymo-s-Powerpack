@echo off
rem Builds Jaymo's Powers and packages the whole modpack:
rem   modpack\Jaymos-Powers-Modpack-<version>.zip  - send this to friends: CurseForge > Create Custom Profile > Import
rem   modpack\mods\                                - every jar, for the server's mods folder
title Jaymo's Powers - build modpack
cd /d "%~dp0"
set "JAVA_HOME=C:\Program Files\Eclipse Adoptium\jdk-25.0.4.101-hotspot"
set "GRADLE_USER_HOME=E:\gradle-home"
call gradlew.bat build packageModpack
if errorlevel 1 (
	echo Build failed - show the text above to Claude.
	pause
	exit /b 1
)
echo.
echo Modpack ready in %~dp0modpack :
dir /b modpack
echo.
dir /b modpack\mods
pause
