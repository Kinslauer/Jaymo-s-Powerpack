@echo off
rem Refreshes the vault's Open PRs note, then opens the project vault in Obsidian at Home.
rem Double-click it, or pin a shortcut to it on the desktop or taskbar.
python "%~dp0vault_prs.py" --open
if errorlevel 1 pause
