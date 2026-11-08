# Setting up the project vault

Git ignores the files Obsidian keeps rewriting (they blocked `git pull`), so their shared defaults live here:

- `*.json`: Obsidian's settings (`docs/vault/.obsidian/`): templates in `Templates/`, new notes in `Live/Agents/`,
  bookmarks for Home, Board, Decisions and Open PRs, and the core plugins the vault uses (Templates, Bases, Bookmarks).
- `Board.base`: the agent board's table. It lives in the live folder, so changing its columns or sorting in Obsidian
  never touches git.

## The live folder

Agent notes, the decision log, Open PRs and the board's table live in **`E:\Jaymo Vault Live\`**, outside git, so
agents working in their own worktrees can write to it (the app blocks Claude's file tools from writing into the main
checkout). The vault shows it as `Live/` through a Windows junction at `E:\Origins Mod\docs\vault\Live`.

## On a new PC, or after Obsidian's settings get lost

Close Obsidian first, then in PowerShell from the repo root (`E:\Origins Mod`):

```powershell
New-Item -ItemType Directory -Force "E:\Jaymo Vault Live\Agents\Done" | Out-Null
if (-not (Test-Path "E:\Jaymo Vault Live\Board.base")) { Copy-Item docs\vault-settings\Board.base "E:\Jaymo Vault Live\" }
if (-not (Test-Path docs\vault\Live)) { cmd /c mklink /J docs\vault\Live "E:\Jaymo Vault Live" }
New-Item -ItemType Directory -Force docs\vault\.obsidian | Out-Null
Copy-Item docs\vault-settings\*.json docs\vault\.obsidian\
```

**Careful with the junction:** to remove it, use `rmdir docs\vault\Live` (removes only the link). Never delete
`docs\vault\Live` recursively or run `git clean` there: that can delete the real notes in `E:\Jaymo Vault Live\`.
