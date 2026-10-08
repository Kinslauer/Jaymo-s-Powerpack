@echo off
rem Builds Jaymo's Powers and gathers the whole modpack into the "modpack\mods" folder:
rem drop those jars into a CurseForge custom profile (Fabric 26.3) or the server's mods folder.
title Jaymo's Powers - build modpack
cd /d "%~dp0"
set "JAVA_HOME=C:\Program Files\Eclipse Adoptium\jdk-25.0.4.101-hotspot"
set "GRADLE_USER_HOME=E:\gradle-home"
call gradlew.bat build
if errorlevel 1 (
	echo Build failed - show the text above to Claude.
	pause
	exit /b 1
)
if exist modpack\mods rmdir /s /q modpack\mods
mkdir modpack\mods
copy /y "E:\jaymospowers-build\Origins-Mod\libs\jaymos-powers-*.jar" modpack\mods\ >nul
copy /y "libs\jaymos-kitchen-*.jar" modpack\mods\ >nul
copy /y "..\Test Mod\multiplayer-mods\fabric-api-*.jar" modpack\mods\ >nul
copy /y "..\Test Mod\multiplayer-mods\balm-*.jar" modpack\mods\ >nul
copy /y "..\Test Mod\multiplayer-mods\cookingforblockheads-*.jar" modpack\mods\ >nul
del /q modpack\mods\*-sources.jar 2>nul
echo.
echo Modpack ready in %~dp0modpack\mods :
dir /b modpack\mods
pause
