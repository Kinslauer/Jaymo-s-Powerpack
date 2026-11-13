---
status: running
task: minecraft-server-bot-experiment
branch: claude/project-thread-f24uld (one small commit: display names in /youth status)
with: 
pr: 1467
mods: kingdoms
versions: 
started: 2026-11-13
owns:
  - E:/hermes/scripts/mcbot.py
  - E:/hermes/skills/minecraft/minecraft-mechanics/
  - E:/hermes/SOUL.md (Minecraft chat rule only)
  - E:/hermes/cron/jobs.json (mcbot-listener job only)
---
# YOUTH in game: the Hermes side of the wood-wisp

**Task:** Andy said "yes start" (Nov 13) to the Hermes side of the Minecraft server bot plan: `mcbot.py`, a chat
listener, a mechanics skill + cheatsheet, a SOUL rule, then tests. Mod side is PR #1467.

## Plan
- Checked: PC reaches the Deck over SSH (`~/.ssh/jaymos_deck`), `./rcon.py list` answers, RCON on.
- Chat in `latest.log` is `<Display Name> text` (display names, e.g. "Sora Stormbour'ne"), but `/youth status` gave only
  account names: add `display` to the status JSON on PR #1467 so the listener can find the asker.
- `hermes chat --oneshot` can't run from Andy's desktop session (gateway is in session 0), so the listener is kept alive
  by a no-agent Hermes cron (runs inside the gateway's session).
- YOUTH only writes the answer; the listener sends it through `mcbot.py say` (YOUTH never builds a console command).

## Notes
- 

## Done
