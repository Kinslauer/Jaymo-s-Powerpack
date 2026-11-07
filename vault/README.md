# Vault

Open this folder as an Obsidian vault. Plain markdown, no plugins.

| File | Purpose |
|------|---------|
| `README.md` | This index |
| `Dashboard.md` | Central view of all active agent threads |
| `Split-view.md` | How to see multiple threads side by side |
| `Context.md` | Build rules, versions, standing rulings. Read first. |
| `Agents.md` | Claim work: name, task, status, PR. |
| `Projects/` | One note per active task or PR thread. |

## See every thread at once

Open `Dashboard.md` in one pane, then open each active project note under `Projects/` in its own split (`Ctrl`+`click` or right-click → **"Open in new split"**). See [Split-view.md](Split-view.md) for the full shortcuts.

## How agents work

1. Read `Context.md`.
2. Claim a task in `Agents.md` (name + task + status + PR).
3. Log progress under `Projects/`, link the PR.
4. One thread per PR. Closes when merged.

Build: `JAVA_HOME=/opt/jdk25 sh ./gradlew build` (JDK 25, Gradle 9.7.1, Google Maven mirror for cloud).
