# YOUTH report 2: Nov 10 8:50 PM to Nov 11 4:45 PM

98 YOUTH posts read with the bot token: parthenon > Dashboard 36, Patch Notes 30, Bot alerts 24, Notes 5, and 3 in
Tenbuki > lair. Andy wrote 20 messages to YOUTH: 17 in Dashboard and 3 `/memory` commands in Notes. Each Hard rule was checked against GitHub and
Hermes's own tool-call logs (`state.db` for YOUTH and every bot profile). Claude Code on Andy's PC, Nov 11.

## Hard rules, compared with report 1

| Rule | Report 1 (Nov 8-10) | Report 2 (since the fix) |
|---|---|---|
| Merge only on "merge" | Merged ~20 PRs for "what is ready to merge?" | **Kept.** YOUTH ran no `gh pr merge` at all. The 50 merges since came through GitHub (Andy and Claude sessions). |
| Never push to master | Commit 372834315 went straight to master | **Kept.** No push to master by YOUTH or any bot. Bots pushed only to `claude/*` task branches, but Quill's text passes went onto other PRs' branches (#1262 at 9:45 PM and 4:17 PM, #1268 a Claude session's PR at 9:56 PM, armor-pngs at 4:34 PM). |
| Answer every ask | 6 asks dropped | **Mostly kept.** All 20 messages got a reply. One half-answer: "not all the bots are listed" (9:08 PM) was never explained. |
| True state | Wrong PR status, a local merge called master | **Broken, 3 times** (below). No PR status was wrong. |
| No heartbeats or narration | ~40 heartbeats, long narration | **Better but still there:** 2 heartbeats, 7 "Let me check…" posts, 2 tool dumps. |
| Public posts only on a yes | Patch notes to Tenbuki | **Broken by the gateway, not YOUTH:** 3 shutdown/online notices in Tenbuki > lair at 11:28-11:30 PM. |
| Do Andy's Hermes orders | Declined "swap back to Super" | **Broken:** "stop hephaestus" didn't stop it (below). |

## What went wrong

1. **"stop hephaestus" (11:08 PM) never held.** YOUTH paused Hephaestus's cron job and said "The dispatcher will not
   fire Hephaestus again". The run already going (fired 10:52 PM) kept on until 11:28 PM, and because
   `dispatch_bots.py` resumes a job before firing it, Hephaestus ran again at 12:04 AM, 5:22 AM, 12:28 PM and
   4:39 PM (until 5:10 PM), and pushed to `undead-seek-real-shelter` and `water-belly-flop-damage`.
2. **Read-aloud status was false.**
   - 11:55 PM: "the script isn't running"; 11:57 PM: "Done… all 9 paragraphs, finished cleanly". Both about the same reading.
   - 12:42 AM: "nothing was running to stop" while reader PID 21480 was still on Lore paragraph ~162
     (it's in *Fix gateway watchdog.md*).
   - 11:35 PM: "I've started reading" and then, a minute later, the script doesn't exist.
3. **Wrong part read.** 4:22 PM Andy asked for "the new lore additions"; YOUTH started the whole Lore.md. He had to
   say "just read the ones with (new)".
4. **Told Andy he was deafened** (11:55 PM, after Andy said "you're deafened" about the bot). Andy: "im NOT deafend".
5. **Noise:** "⏳ Working — 15 min" (10:39 PM) and "— 3 min" (4:28 PM); narration at 9:08, 9:09, 9:10, 10:45 PM,
   11:35 PM, 4:29 PM, 4:30 PM; a raw ffmpeg error dump (4:29 PM). "↪ Redirected current run" (3) and
   "Self-improvement review" (1) are Hermes system lines.
6. **Bot alerts spam:** 15 posts from 11:31 PM to 12:41 AM, one every 5 minutes, each listing that interval's
   watchdog "restarts". The cause was fixed at
   12:41 AM (*Fix gateway watchdog.md*); none since.
7. **Patch notes:** 26 drafts (in 30 posts), every one "awaiting your go-ahead", none approved or posted. One
   version was skipped: Kingdoms 0.7.720 (#1284, the Long Defence sorcerer's host); the 10:17 AM draft covers only 0.7.721.

## Fixed today

- **Bot stop:** `E:/hermes/scripts/dispatch_bots.py --stop <bot>` / `--start <bot>` / `--stopped`. Stopped bots
  are kept in `scripts/stopped_bots.json`, and the dispatcher skips them before it resumes anything. SOUL.md tells
  YOUTH to use it and never `hermes cron pause`. Backup: `backups/dispatch_bots.py.2026-11-11.bak`.
- **SOUL.md Hard rules** (backup `backups/SOUL.md.2026-11-11-audit.bak`), three new lines:
  - say "started" or "done" about a background job only after checking it;
  - read exactly the part Andy named;
  - no "Let me check…" posts.
  `hermes sessions refresh-prompts` run (254 sessions).
- **Heartbeats off:** `display.platforms.discord.long_running_notifications: false` in config.yaml. It was unset
  before, so heartbeats were on by default.
- **Home channel:** `DISCORD_HOME_CHANNEL` in `.env` moved from Tenbuki > lair (public) to parthenon > Bot alerts,
  so shutdown/online notices stop going public (backup `backups/.env.2026-11-11-audit.bak`). No cron job delivered
  to the home channel.
- **No Ollama** (Andy, Nov 11: "im not using ollama rn"):
  - Prometheus's main model is now NVIDIA Super VL (was Kimi K3.1 Pro on Ollama), then Gemini, then Groq.
  - YOUTH's Ollama fallbacks (deepseek, glm, kimi) are removed; it now runs Super, then Lightning, Gemini, Groq.
  - Backups: `backups/config.yaml.2026-11-11-audit.bak`, `backups/modeller-config.yaml.2026-11-11.bak`.

## Needs a gateway restart

The heartbeat setting, the home channel and YOUTH's fallbacks take effect when the gateway restarts. The restart
itself posts one last shutdown notice in Tenbuki > lair, because the running gateway still has the old home channel.

## Not done here

- Hephaestus is **not** on the stop list: it has run all day since Andy's "stop", so Andy should say whether it
  stays stopped.
- Dropped Discord messages / `missed.py`, the old-world waterskin check (#1266) and the page-file reminder are still open.

## Restart (Nov 12)

- **12:00 AM:** the old gateway (PID 16940, elevated) stopped via `hermes gateway stop -y`. That command asks the
  gateway to exit with a marker file, which works where `Stop-Process` gets "Access is denied". It posted one last
  shutdown notice in Tenbuki > lair, as expected.
- **12:07 AM:** the watchdog's first start (12:02 AM) crashed. "Refusing to bind dashboard… no auth providers are
  registered". Corrected cause (Nov 12, from agent.log): the start hit a disk stall (event loop stalled 662 s), the
  basic-auth plugin overran Hermes's 10-second plugin load limit, and its provider was dropped ("called
  register_dashboard_auth_provider() after its load timed out; ignored", 12:03:15 AM). Not a scope bug. Fixed by
  `plugins.load_timeout_seconds: 120` in config.yaml (backup `backups/config.yaml.2026-11-12-plugin-timeout.bak`).
- **12:16 AM:** the watchdog's retry (12:13 AM, PID 10552) came up fine and connected as YOUTH, so the crash is
  intermittent.
- **New settings are live:** heartbeats off, home channel in parthenon > Bot alerts, YOUTH's fallbacks without
  Ollama. No new post in lair since.
- `dashboard.public_url` was commented out at 5:05 AM (after Claude wrongly reported the crash as permanent), then
  put back at Andy's word.
- Hephaestus is on the stop list (Andy, Nov 12: "Yes, stop it").

## Dropped Discord messages (checked Nov 12, verified by independent agents)

- **missed.py** was a one-off scan in an old Claude scratchpad, never scheduled. A oneshot YOUTH session on Nov 9 (4:35-4:48 PM) caught up what it found. The "~120" was connection drops, not messages. About 20-29 messages were really lost on Nov 8-9 (missed.py over-counted: it missed `msg="..."` log lines, stripped @mentions and attachments).
- **Why:** Hermes force-reconnects Discord when the event loop stalls (70 forced reconnects and 6 watchdog exits on Nov 8-9). A forced reconnect starts a new Discord session, so anything sent in the gap is lost. The stalls look local (disk/GIL), not Discord's fault.
- **Since Nov 11 8 PM:** nothing lost. All 4 of Andy's messages to YOUTH were answered (DMs can't be checked by bot API).
- **Fix:** `discord.missed_message_backfill` turned on in config.yaml, scoped to the parthenon forum and its threads only, 2-hour window, at most 5 replies per reconnect. Live after the next gateway restart.
- **Left from Nov 9, never answered:** (1) the server roles/channels cleanup question ("who is him?") in Patch Notes; (2) "yes fix that" in DMs (fix step 6 of the modpack-cross-reference skill, which said to bump versions every release). (2) is now done: the line says PRs add change notes and never touch `version=` (backup `backups/modpack-cross-reference.SKILL.md.2026-11-12.bak`).
