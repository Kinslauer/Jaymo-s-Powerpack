# Decisions

Every decision Andy makes, newest **last**. Append with `Templates/Decision.md`. Standing rulings from before this
log started are in `docs/AI-HANDBOOK.md` section 7 (see [[Rules]]).

### 2026-11-06: project vault
- **Decided:** the vault lives in the repo at `docs/vault/`, committed and shared by every agent. Obsidian installed
  on the PC with winget.
- **Applies to:** all three mods; every agent session.
- **Agent:** [[Done/obsidian-vault-4d9705|obsidian-vault-4d9705]]

### 2026-11-06: agents work only in their own worktree
- **Decided:** every agent, Hermes included, works only in its own git worktree, never switches branches in a shared
  checkout, and never runs `git checkout -- .`, `git clean -f` or `git reset --hard` (Hermes lost unsaved work that way).
- **Applies to:** every agent session, every assistant.
- **Agent:** the project thread (PR #1100 review session).
