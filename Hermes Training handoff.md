# Hermes Training: handoff to Claude Code on Andy's PC

Paste this into Claude Code on the PC (or say "read Hermes Training handoff.md in the vault and take over").
Written Nov 11, 2026 by the cloud Claude in the "Hermes Training" project, which can't reach this PC.

## Your job

You take over training **Hermes**, Andy's bot setup in `E:/hermes`: **YOUTH** (the default profile, his Discord
assistant) and the worker bots. Watch what they do, find where they go wrong, fix their rules, config and scripts,
and keep Andy's asks moving. You can reach `E:/hermes`, `E:/Origins Mod` (the repo) and Discord (with YOUTH's token),
which the cloud thread couldn't.

## Andy's standing rules

- **Merge only on Andy's word** ("merge"). Never merge from a question, and never push to master: every change goes
  through a PR. The Release workflow bumps versions. PRs never touch `version=`, MODLOGs or `docs/codemap/`
  (read `CLAUDE.md` and `changes/README.md`).
- **Never print, log or paste a token or key**, not in a command, a file or a chat.
- Short, plain reports ("ready to merge as PR #N"). Times on a 12-hour clock, Eastern.
- Every decision Andy makes: append it to the vault's `Decisions.md`.
- Ask before anything that can't be undone (deleting data, posting in a public Discord channel, spending API credit).
  Andy is out of Anthropic Console credit, so **no cloud-agent runs** (`tools/cloud-agent`) unless he says so.

## How Hermes is set up

- Hermes home `E:/hermes`; profiles in `E:/hermes/profiles/<name>`. YOUTH's rules: `E:/hermes/SOUL.md`
  (the "Hard rules" section from Nov 10 is the audit fix; keep it).
- Talk to Hermes from `E:/hermes`, in the background, with no short timeout:
  `HERMES_HOME=E:/hermes tools/python-3.14.9+20261021-win32-x64/python.exe -I bin/hermes.exe chat -q "<msg>" --oneshot -Q`
- Restart the gateway only with `schtasks /run /tn Hermes_Gateway` (or the watchdog), **never**
  `hermes gateway restart`. `schtasks /run` does nothing while the gateway is up; to make sessions pick up a new
  SOUL.md, run `hermes sessions refresh-prompts`.
- Bots (vault labels): Apollo `hermes-a`, Bacchus `hermes-b`, Hephaestus `hermes-fixer`, Prometheus
  `hermes-modeller`, Quill `hermes-writer` (the only writer since Nov 10). The `dispatch-bots` cron fires them.
  Cap: 2 counted bots at once, never two builders; Prometheus counts. Logic: `E:/hermes/powerpack/vault.py`,
  `E:/hermes/scripts/dispatch_bots.py`, `bot_health.py`.
- Models in `E:/hermes/config.yaml`: YOUTH on NVIDIA Super first (Andy's call), then free fallbacks. Keep ONE
  `providers:` block; check with `hermes fallback list`. Prometheus runs on Ollama Cloud, which hit its monthly 429
  limit Nov 9 (`Logs/prometheus-error.md`).
- Python: pip installs into Python 3.12 (`py -3`), Hermes runs its own 3.14.9. Install tools for YOUTH's terminal
  with `py -3 -m pip`.
- Work flows through the vault (`E:/Jaymo Vault Live`, synced by `python tools/vault_sync.py`): tasks in `Inbox/`,
  board in `Kanban.md`, decisions in `Decisions.md`. After a PR passes review: `vault_sync.py --note`, then `--send`;
  YOUTH posts it in Discord Hotspot > parthenon > PR Merges (link, what the player sees, what wasn't tested, verdict).
- Discord board: `tools/discord_kanban.py` (one markdown message plus a Move menu in parthenon > Kanban). Env
  `KANBAN_BOT_TOKEN` (YOUTH's token) and `KANBAN_MOVERS` (Andy's id) are set. Keep it running; stop the old
  `discord-kanban` cron if it's still on. Read aloud: `tools/discord_read_aloud.py` (see `Read aloud setup.md`).

## Read these first (vault)

- `YOUTH report.md`: the Nov 8-10 audit of 525 YOUTH messages. It shows the bad habits: merging when asked a question,
  pushing to master, dropped asks, false status, Discord noise.
- `Fix YOUTH.md`: what was fixed Nov 10 (all boxes ticked).
- `Fix Hermes stalls.md`, `Logs/hermes-stall.md`: Nov 11 fixes (10-minute command timeout, bots can't mark a task
  in review unless the commit is on GitHub with green checks).
- `Fix gateway watchdog.md`: the watchdog thinks the gateway is down and "restarts" it every minute; YOUTH got stuck
  in a voice channel. **Still open**: work through it.

## Still open

1. **Re-audit YOUTH** (due around Nov 12): read YOUTH's Discord messages since Nov 10 8:50 PM with the bot token, the
   way `YOUTH report.md` was made, and check each Hard rule: any merge without "merge"? any push to master? any
   unanswered ask? any wrong PR status? heartbeat or narration spam? Write `YOUTH report 2.md`, compare with the
   first, fix what's still wrong (SOUL.md, skills, config), and tell Andy in 5 lines.
2. **Gateway watchdog loop**: `Fix gateway watchdog.md`.
3. **Dropped Discord messages** (~120 on Nov 8-9): check whether `missed.py` caught them up, and why YOUTH misses
   messages at all.
4. **Prometheus**: has the Ollama limit reset, or does it need another provider? `bot_health.py` should flag it.
5. **Waterskins (#1266, merged)**: load an old world (from before Kitchen 0.5.98) once before the next server
   deploy, and check the waterskins come through as Leather Waterskins.
6. Page file move script (`E:/hermes/scripts/move_pagefile_to_ssd.ps1`): Andy runs it himself; remind him once.

## When you finish something

Write the result into the vault (a note, or a line under the task), run `python tools/vault_sync.py`, and tell Andy
in one short Discord post or here. The cloud thread reads the vault, so anything written there reaches it.
