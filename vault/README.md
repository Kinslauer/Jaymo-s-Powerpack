# Vault

Open this folder as an Obsidian vault. Plain markdown, no plugins.

| File | Purpose |
|------|---------|
| `README.md` | This index |
| `Dashboard.md` | Central hub — side menu sorts PR threads by status (in progress / idle / waiting for review / resolved) |
| `Context.md` | Build rules, versions, standing rulings. Read first. |
| `Agents.md` | Claim work: name, task, status, PR. |
| `Projects/` | One note per active task or PR thread. |

## How agents work

1. Read `Context.md`.
2. Claim a task in `Agents.md` (name + task + status + PR).
3. Log progress under `Projects/`, link the PR.
4. One thread per PR. Closes when merged.

Build: `JAVA_HOME=/opt/jdk25 sh ./gradlew build` (JDK 25, Gradle 9.7.1, Google Maven mirror for cloud).
