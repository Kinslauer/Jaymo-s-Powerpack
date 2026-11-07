# Jaymo's Powerpack — Obsidian Vault

This vault holds the shared project context for Jaymo's Powerpack (Minecraft 26.3 Fabric modpack: Jaymo's Powers, Kingdoms, Kitchen). It is designed so **three agents can work at once on the same page** — each agent reads `Context.md` for the ground truth, claims work in `Agents.md`, and logs progress under `Projects/`.

## How to use it

- **Open this folder as an Obsidian vault.** Everything here is plain markdown; no plugins required.
- **Context first.** Read `Context.md` before starting any task. It has the build rules, versions, and standing rulings (from `CLAUDE.md` and `docs/AI-HANDBOOK.md`).
- **Claim before you edit.** Add your name and task to `Agents.md` so the other two agents know what's in flight.
- **Log as you go.** Write a short note under `Projects/<task>.md` when a task starts, finishes, or blocks. Link back to the PR number when one exists.
- **One thread per PR.** Each PR is its own isolated thread — its own branch, its own context, closes when merged. This vault is what the threads share between them.

## Folder layout

| File | Purpose |
|------|---------|
| `README.md` | This index |
| `Agents.md` | Who the three agents are and what each is working on right now |
| `Context.md` | Shared ground truth: build rules, versions, standing rulings, current pack state |
| `Projects/` | One note per active task or PR thread |
| `Templates/` | Reusable note templates (agent task log, PR summary) |

## Where the code lives

The modpack repo is `Kinslauer/Jaymo-s-Powerpack`, default branch `master`. Build: `JAVA_HOME=/opt/jdk25 sh ./gradlew build` (JDK 25, Gradle 9.7.1, Google Maven mirror for cloud).
