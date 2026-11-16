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
Hermes side built and tested off-server; chat paused. A security review (separate agent) found no way for chat or model
text to become a console command; its kill-switch, rate-limit and robustness gaps are fixed and retested (bad ask
files skipped, `off` refuses after a wait too). Players see nothing yet: needs #1467 merged + pbdr, then `mcbot.py on`
and an in-game test with Andy (not done). Answers take 10-150 s depending on NVIDIA's queue.
- 2026-11-13 19:17 PR #1467 is merged into master; moved to Agents/Done.

## Nov 13, 8:30 PM: proximity chat + abuse filter (Andy's ask)
- Why the first test got no answers: Kingdoms' proximity chat cancels vanilla chat, so the log only has
  `[chat] <display> (<account>) talk|whisper|shout|act: <text>` and `[ooc] <display> (<account>): <text>`.
- The listener now reads those AND `[youth-ask] <account> <question>` (PR #1481, merged; not on the Deck yet: it runs
  Kingdoms 0.8.42). The account in brackets goes straight to `/youth summon|say`. One message = one question (10 s
  per account), so the `[chat]` and `[youth-ask]` lines of the same message don't double up.
- Abuse filter (`abusive()` in mcbot.py): slurs, abuse, threats and insults at Youth are dropped silently, before
  anything is logged, summoned or answered; the log says only "dropped a line from <account> (abuse filter)". YOUTH's
  answers go through the same filter and are never said if they hit it. Offline test: 0/25 clean game questions
  blocked, 0/22 abusive lines (leetspeak, spaced-out letters, repeats) let through.
- Checked: listener pid 20000 running the new code, mcbot ON. Not tested live: the server crashed at 8:17 PM
  (ArrayIndexOutOfBoundsException in the tick loop, heap 6.8/8 GB, nothing from Youth in it) and is down; needs pbdr,
  which would also put #1481 on it.
