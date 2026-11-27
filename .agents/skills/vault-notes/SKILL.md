---
name: vault-notes
description: Use when you start or finish a task on the repo, pick up work from the vault Inbox, share a PR with another agent, or Andy makes a decision you should record.
---
# Working with the project vault

The vault is `docs/vault/` (Obsidian). Its live notes sit outside git in `E:/Jaymo Vault Live/` (shown in the vault as
`Live/`) and sync with the `vault-live` branch through `tools/vault_sync.py`. In a cloud session run
`python3 tools/vault_sync.py --setup` once to get them in `docs/vault/Live/`. Claude Code's hooks run the sync for you;
other tools run `python tools/vault_sync.py` when they start and after changing a live note.

When you start:
- Read `docs/vault/Home.md` (versions, guides) and `Live/Dashboard.md` (what waits on Andy, what's stuck; problems
  in the live notes are on `Live/Health.md`).
- Check `Live/Inbox/` for tasks the coordinator posted. The pick-up and review loop, with the commands, is in
  `docs/vault/Pipeline.md`.
- Make or join your agent note in `Live/Agents/` from `Templates/Agent.md`: branch, PR, the files you own. Never in
  your worktree, and never edit a file another running agent owns.

While you work: append each of Andy's decisions to `Live/Decisions.md`.

When you finish: update your agent note.

Sharing a PR with another assistant (Claude, Hermes, Codex): follow "Two agents on one PR" in
`docs/vault/How agents use this vault.md`, which has the full routine.
