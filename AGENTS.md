# Instructions for AI assistants

Follow `CLAUDE.md` (the project rules; they apply to every assistant, not only Claude), then read
`docs/AI-HANDBOOK.md` (Andy's standing rulings, build setup, version and merge workflow, pbdr) and `docs/HANDOFF.md`.
Project memory, reference notes and the modpack readout are in `docs/ai-bundle/` (start with its README).

Before you start, read `docs/vault/Home.md` (current versions, open PRs, what's next) and follow
`docs/vault/How agents use this vault.md`: make or join a note in `E:/Jaymo Vault Live/Agents/` (the live
folder outside git, not your worktree) listing your branch, PR and the
files you own, never edit a file another running agent owns, and append each of Andy's decisions to
`E:/Jaymo Vault Live/Decisions.md`. Sharing a PR with another assistant (Claude, Hermes, ...): see that note's
"Two agents on one PR" section. Run `python tools/vault_sync.py` when you start and after you change a live note
(it syncs the live folder with the `vault-live` branch), and check `E:/Jaymo Vault Live/Inbox/` for tasks the
coordinator posted for you. The pick-up and review loop, with the exact commands, is in `docs/vault/Pipeline.md`.
