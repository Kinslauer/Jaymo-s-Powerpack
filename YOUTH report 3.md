# YOUTH report 3: Nov 11 4:45 PM to Nov 17 12:30 PM

I read 396 YOUTH posts with the bot token:

| Where | YOUTH posts |
|---|---|
| parthenon > Patch Notes | 282 (202 of them from the patch-notes cron job) |
| parthenon > Dashboard | 41 |
| parthenon > Questions | 32 |
| parthenon > Bot alerts | 31 |
| parthenon > Experiments | 8 |
| parthenon > Needs Me | 1 |
| Tenbuki > lair | 1 |

Andy wrote 53 messages in those threads. DMs can't be read this way.

Each hard rule was checked against two sources:
- GitHub (`gh`, master's first-parent history).
- Hermes's tool-call logs: `state.db` for YOUTH and for each bot profile.

The Kanban thread's board is edited in place, so it isn't counted. Claude Code on Andy's PC, Nov 17.

## Hard rules, compared with report 2

| Rule | Report 2 (Nov 10-11) | Report 3 (Nov 11-17) |
|---|---|---|
| Merge only on "merge" | Kept | **Broken once.** At 1:16 PM on Nov 16, Andy said "Approve all" about the memory list YOUTH had just posted. YOUTH ran `gh pr merge 1642/1643/1644 --squash` instead (below). It owned the mistake when Andy pointed it out. |
| Never push to master | Kept | **Kept.** Every commit on master since Nov 11 came from a PR, a release, or a Claude session's direct fixes. YOUTH and the bots pushed only to task branches. Quill still pushes its text passes onto other PRs' branches: `claude/project-thread-*` (Nov 11, Nov 12), `hermes-a/sky-lands-aether-biomes` (Nov 15) and `claude/punch-barrage-effects` (Nov 16, Nov 17). Apollo pushed `vault-live` by hand five times between 8:39 and 8:46 AM on Nov 17. |
| Answer every ask | Mostly kept | **Mostly kept.** Two asks were lost or half-done (below). Two "I'll let you know when it's done" promises were never kept. |
| True state | Broken 3 times | **Better, two slips.** The Kanban answer contradicted itself. Patch notes twice said "up to 0.22.51" when master was further on (below). The classes answer was wrong until Andy told it to re-read master. After that it was right: 14 classes, matching `Classes.java`. |
| No heartbeats or narration | 2 heartbeats, 7 "Let me check…" | **Heartbeats gone. Narration not:** about 38 "Let me…" posts, despite the SOUL.md rule (below). |
| Public posts only on a yes | Broken by the gateway | **Kept.** One shutdown notice reached Tenbuki > lair, at 12:00 AM on Nov 12. That was the planned restart that moved the home channel. Nothing public since. No patch-note draft went out. |
| Do Andy's Hermes orders | "stop hephaestus" didn't hold | **Kept.** Hephaestus went on the stop list at 7:01 AM on Nov 12. Its last activity was its final run on Nov 11, 4:39 to 5:10 PM; nothing since. Memory approval was done once Andy restated it. |

## What went wrong

1. **"Approve all" became a merge, Nov 16 1:16 PM (Questions).**
   - YOUTH had just posted 4 pending memory writes ending "Apply: /memory approve <id>". Andy replied "Approve all".
   - YOUTH read it as "Tiv is Andy — 'approve all' means merge the open PRs" and merged #1642, #1643 and #1644. GitHub has them merged as Kinslauer at 1:16:42 to 1:16:52 PM.
   - It also removed six worktrees and moved the three tasks to Done, in 9 narration posts.
   - All three had green checks and nothing went wrong on master. But the SOUL.md rule is "merge only on 'merge'", and the memories were what Andy meant.
2. **Two "I'll notify you" promises were dropped.**
   - At 8:25 PM on Nov 16, Andy said "have someone pickup the open task and notify me when it is done". YOUTH said it would notify him when `future-gun-gear` reached in-review. That PR, #1671, opened at 10:44 PM and merged at 5:15 AM on Nov 17. No post was made.
   - At 9:29 PM on Nov 16, YOUTH said "I'll let you know when Bacchus finishes" the punch effects. #1665 and #1667 merged at 8:25 AM on Nov 17. No post was made.
   - Nothing in YOUTH watches for this. It can only report when Andy writes to it.
3. **Two patch notes covered the wrong range.**
   - Nov 12, 8:53 AM: Andy asked for "just headings… everything since the last post". YOUTH headed it "Jaymo's Powers 0.22.51 (Kingdoms 0.7.753)", but master was already at Powers 0.22.65 and Kingdoms 0.7.762, so 14 Powers versions were missing.
   - Nov 12, 7:50 PM: the "large log" was again "up to Powers 0.22.51" while master was at 0.22.84. It came in 30 parts, and two failed for being over Discord's 2,000 characters. Andy cancelled it ("this is too much").
   - Nov 14, 11:22 PM: Andy asked for "everything i pushed today". YOUTH wrote 12 parts covering everything since Powers 0.22.0 (Nov 10). The day began at Powers 0.23.48 and Kingdoms 0.8.47. The Xeno-Band line says "Sixteen aliens" and then lists 23 names, with Rivet twice.
   - YOUTH reused its last notes' version instead of reading master, and treated "since the last post" as "since the last compiled notes".
4. **A message was lost on Nov 16 at 9:12 AM:** "Can you have a bot pickup the crash sweep?"
   - The gateway log shows Hermes batched the 42-character message.
   - Discord's socket went stale at 9:14:53, and the batch was flushed while disconnected: `Error handling message: not_found` at 9:15:00.
   - The missed-message backfill then counted it as already seen (`scanned=2 missed=0`).
   - Andy got an answer only after "Hello?" at 11:06 AM, 1 hour 54 minutes later. This is a Hermes bug, not YOUTH's judgement: backfill doesn't cover a batch that fails mid-reconnect.
5. **The Kanban answer contradicted itself (Nov 16 5:32 PM).** "Yes, the Kanban is up to date" listed `burnt-corpse-ash-piles` and `faith-page-champion` under both Needs Andy and Done.
6. **The Experiments thread (Nov 13 7:52-8:10 AM).**
   - Andy said "those entries go here", meaning the two experiments YOUTH had just logged in the Dashboard. YOUTH had no context in the new thread and asked twice what he meant.
   - It never posted the summaries in the thread, though at 7:55 AM in Dashboard it had called them "ready to post there".
   - It moved OmniRoute and Pirate Face to `Inbox/Done` as "done", although Andy had asked to log them "for later".
   - The "minecraft-server-bot" experiment is also marked done in the vault. YOUTH had told Andy "nothing to start" when he said "lets start on the server bot experiment" at 11:40 AM. (A Claude session later built the wood-wisp instead.)
   - OpenCode was listed as experiment 3 "queued", but it has no task file.
7. **Task routing.** Andy asked for hands-on cooking to be prepared "as a task for reviewer claude tmr morning" (9:48 PM, Nov 16). YOUTH filed it `for: hermes`, so Apollo and then Bacchus picked it up overnight. Bot alerts showed both stalled on it, and it is `blocked` now.
8. **Creating the thread failed in public.** On Nov 12 at 11:07 PM, "make a new thread called experiments" took 7 narration posts (11:10 to 11:12 PM), and only at 11:16 PM did YOUTH say the tool can't open a forum post. Andy made it himself.
9. **Noise:**
   - About 38 "Let me…" or "Now I'll…" posts. The worst was a run of 9 for the patch notes at 11:22 PM on Nov 14.
   - 4 "Self-improvement review" posts and 5 "Interrupting" or "Redirected" lines; those are Hermes system lines.

## Went well

- Every hard rule but one held for six days. There was no push to master and no public post. Hephaestus stayed stopped, and heartbeats are gone.
- Voice message transcription (Nov 16 12:14 PM) and the dialogue-system task notes were captured accurately, including Andy's "hints, not answers" direction.
- The answers were checked and correct:
  - #1245 was already closed (Nov 10).
  - #1313 and #1376 were open and waiting on Nov 12.
  - There is no "awakening second pass" anywhere in the vault, git or the PRs.
  - The class list on Nov 16 matched `Classes.java` once it re-read master.
- Bot alerts behaved: 31 posts, each a real stall, a dispatcher gap or a gateway restart, and each cleared.

## Gateway health (from Bot alerts)

The watchdog restarted the gateway on:
- Nov 12, 12:02 and 12:13 AM.
- Nov 13, 6:51 PM.
- Nov 15, 9:00 to about 10:25 PM, every 10 minutes. The cause was a Hermes update that paused the gateway (Quill's runs failed with `Gateway draining: update_paused`).
- Nov 16, 7:02 PM.

## Suggested fixes (not done; Andy to pick)

1. **SOUL.md:** "approve" (or any word other than "merge") never merges a PR. When the last thing YOUTH posted was a list of memories, a yes or "approve" applies to that list.
2. **SOUL.md:** patch notes always read the current versions on master before they say "up to X". "Since the last post" means since the newest notes in the thread, and "today" means today's merges.
3. **Promised notifications:** when YOUTH says "I'll let you know", it writes a watch for that PR or task. A no-agent cron job, beside `merged_prs.py`, would post when the PR opens or merges.
4. **Hermes bug report or patch:** a text batch flushed during a Discord reconnect is dropped, and the backfill doesn't redo it. Backfill should re-dispatch messages whose handling failed.
5. **SOUL.md:** "for reviewer Claude" or "for Claude" in a task request means `for: claude` (or the coordinator), not `for: hermes`.
6. **Narration:** the "no 'Let me check…' posts" rule isn't holding. Consider `display.platforms.discord` options that hide interim assistant text, if Hermes has one, rather than relying on the prompt.

Findings checked by an independent verifier agent on Nov 17. Its corrections are applied: the gap time, the thread-creation timing, Hephaestus's last run, and a traceback claim, which is removed.

## Fixes done (Nov 17, Andy: all six)

1. **"Approve" never merges:** SOUL.md Merging rule. Only "merge" merges; "approve all" after a memory list approves the memories.
2. **Patch notes:** a new SOUL.md "Patch notes" section.
   - Fetch master and read its versions before writing "up to X".
   - "Since the last post" means after the newest notes in the thread; "today" means since midnight.
   - Name the range in the first line, keep parts under 1,900 characters, and ask before going past about 10 parts.
3. **Promises:**
   - `E:/hermes/scripts/watch.py` (`add --pr N` / `add --task T`, `list`, `remove`).
   - The no-agent cron job `promise-watch` runs every 5 minutes (`promise_watch.py`). It posts in Dashboard when a watch comes true. The wisp tells Andy (Manigraphic78) in game too, now or when he next comes on within a day (`watches.ingame.json`).
   - SOUL.md: never say "I'll let you know" without adding a watch.
   - Tested on #1671, #1686 and the future-gun-gear task.
4. **Lost messages after a reconnect:** a local patch to `hermes-agent/plugins/platforms/discord/adapter.py`.
   - The reconnect's backfill now re-runs a message whose live turn failed while it's still in the 5-minute dedup cache (`_discord_message_live_failed`). `max_attempts` still caps it.
   - A new test in `tests/gateway/test_discord_missed_message_backfill.py` fails without the patch; all 21 pass with it.
   - Backup: `backups/discord-adapter.py.2026-11-17-backfill-failed.bak`.
   - **A Hermes update may overwrite this patch:** re-apply it from the backup diff, or report it upstream.
5. **"For Claude" routing:** SOUL.md. "For reviewer Claude" and the like make a task `for: claude`, which the Hermes bots don't take.
6. **No "Let me…" posts:** config.yaml `display.platforms.discord`: `interim_assistant_messages: false` and `streaming: false`.
   - Only YOUTH's final answer goes to Discord.
   - Backup: `backups/config.yaml.2026-11-17-no-interim.bak`.

SOUL.md backup: `backups/SOUL.md.2026-11-17-report3.bak`. `hermes sessions refresh-prompts` was run (580 sessions). The gateway was restarted at 2:51 PM so the patch and the config load.
