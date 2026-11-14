---
status: done
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
- Built (Nov 13, 7 PM): `E:/hermes/scripts/mcbot.py` (verbs who/where/status/summon/say/dismiss/off/on, listen,
  ensure-listener, stop-listener, ask, answer), `mcbot_listener.py` + Hermes cron `mcbot-listener` (every 1 min,
  no agent, id 718bc8bcb6b0), skill `skills/minecraft/minecraft-mechanics` + `references/mechanics-cheatsheet.md`
  (83 sections, 11k words, 690 claims checked by a second reader, 110 fixed), SOUL section "Minecraft server: Youth".
  Backup `E:/hermes/backups/SOUL.md.2026-11-13-minecraft.bak`. State/logs in `E:/hermes/mcbot/`.
- In-game answers: the listener picks the matching cheatsheet sections and asks YOUTH with NO file/skill/terminal
  tools (`-t todo`, `--source tool`, `--reasoning low`), so a player's words can't make it change anything. YOUTH only
  writes `<say>…</say>`; the script sends it through `/youth say` (or `tellraw` until #1467 is on the server).
- Kill switch is OFF (chat ignored) until Andy OKs an in-game test: `py -3 E:/hermes/scripts/mcbot.py on`.
- PR #1467: added `display` to `/youth status` (chat shows display names, e.g. "Sora Stormbour'ne"), commit 492267aec.
- Gateway trouble seen tonight (not from this work): 6:26 PM a 326 s idle GC ("mem_trim") froze the gateway, Discord
  dropped; 6:49 PM its liveness watchdog exited (code 75) and it stayed down until `schtasks /run` at 6:54 PM.
- Old listener pid 13320 (first version) is still alive in session 0 (couldn't kill it from the desktop). It obeys
  only `E:/hermes/mcbot/OFF`, which stays in place, so it's deaf until the next reboot. The real kill switch is now
  `E:/hermes/mcbot/PAUSED` (`mcbot.py off`/`on`). Don't delete `OFF`.

## Done
- 2026-11-13 19:17 PR #1467 is merged into master; moved to Agents/Done.
