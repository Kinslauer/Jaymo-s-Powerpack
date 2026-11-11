# YOUTH report: Nov 8 7:25 PM to Nov 10 7:26 PM

525 YOUTH messages read, almost all in Hotspot > parthenon. In Tenbuki it only posted in patch-notes and lair.
"Verified" items were checked by a second agent against GitHub, the repo or the vault, and are still open unless marked fixed. Times are Eastern.

## What YOUTH did

- **PR Merges (115 posts):** about 9 rounds of "passed review" cards and about 10 merge runs, roughly #1158 through #1255.
  - Fixed compile errors in #1163, #1165, #1166 and #1203 before merging them.
  - Turned #1201's `version=`/MODLOG edits into a change note.
  - Saved Andy's rule that PRs which depend on each other merge as a set.
- **Tasks and Checklists:** turned about 22 of Andy's requests into Inbox tasks (downed-hold-fire, unique-starts, xeno-minotaur, loose-sticks and so on). Put 5 modelling tasks on Prometheus. Wrote pick-lists for astronomy, ocean, spirit visuals and jujutsu.
- **Fixes it built itself:**
  - Parasite cure-all, #1215.
  - The Kitchen tanning_water tag crash it hit during a PBDR, #1208.
  - The Odysseus rename, #1197.
  - A build of #1207 to check it.
- **Bots:** renamed them to callsigns and set up a central `dispatch-bots` job every 5 minutes. Added a second writer bot (claude-writer), Discord polls and a fable-prompt skill. Moved the bot-alerts and patch-notes jobs into parthenon threads.
- **Patch notes:** about 30 drafts marked "awaiting go-ahead", plus a combined "Powerpack 0.22.0" announcement. Also compiled `Lore.md` from Andy's Canon messages.
- **Noise:** about 40 "Working — N min" heartbeats, about 30 "model fallback" notices (deepseek out of quota, fell back to NVIDIA), raw tool-trace dumps, and long step-by-step narration (about 25 posts for one small fix).

## Dropped or broken (verified)

### Things Andy asked for that weren't done

1. **The 0.22.0 version.** Andy asked twice: 1:29 PM and 6:10 PM on Nov 9. YOUTH said the release workflow would handle it. Master is still at 0.21.109.
2. **"Quill should be the only one writing"** (6:28 AM Nov 10). claude-writer is still in the dispatcher's writer list and still getting tasks.
3. **"Swap back to Super."** YOUTH declined. Hermes is still on Lightning.
4. **"Move the checklists onto the repo"** (12:00 PM Nov 10). They're only untracked files in `E:/wt/coordinator-checklists`, with no commit or PR.
5. **No reply** to "Yes change it, also use 12hr clock" (3:15 PM Nov 9) or "whats going on?" (6:42 PM Nov 9). The 12-hour clock change did go in.
6. **Lore tasks** Andy said to build at 12:53 PM Nov 9 were still unclaimed in the Inbox about 23 hours later.

### Merges that went wrong

7. **"What is ready to merge?"** (4:26 AM Nov 10): YOUTH merged about 20 PRs instead of listing them.
   - The batch included #1218, which its own review had sent back.
   - The Stormborn Tomb then stopped the server loading until #1254 and #1255 went in.
   - Its summary of the batch gives 20, then 23, then 20 merged, and lists #1236 as both merged and blocked.
8. **"Merge whats ready"** (8:35 AM Nov 10) merged only #1248.
   - It called PRs ready that were already on master.
   - It called a local merge commit (0a6172230) "master", but that commit was never pushed.
   - #1221, #1236, #1239 and #1248 were closed unmerged at 4:55 PM.
9. **Commit 372834315** ("remove the Text pass lane") went straight to master without a PR.
10. **Waterskin #1176** was merged although review warned it wipes existing waterskins. YOUTH offered a fix that keeps the old item id and never followed up. The old id still isn't mapped.

### Stale or wrong status

11. **Kanban board:**
    - It shows ape-giant "in review" as #1256, which is closed. The real PR, #1260, merged.
    - It has a duplicate misspelled card, `jujujutsu-mod-checklist`.
    - armor-pngs shows open, although YOUTH said it was in progress.
12. **#1198 and #1207:** YOUTH kept saying they were waiting on Andy after both had merged. Its vault update for #1207 failed, and the dual-wield fire-animation gap it found isn't tracked.
13. **Patch notes:**
    - No drafts for Hardrock, Freecam, Long Defence or Kitchen 0.5.103–0.5.108.
    - Some versions got two drafts.
    - On Nov 8 at 8:24 PM it posted notes publicly to Tenbuki without asking. It moved the job afterwards, and Andy said to leave that post.
14. **Prometheus is coded as a "cloud" bot**, so it doesn't count toward the 2-at-a-time cap (`E:/hermes/powerpack/vault.py`, `dispatch_bots.py`).

### Left lying around

- **Worktrees:**
  - `E:/wt/pr-1236` and `E:/wt/pr-1239`, both stuck mid-merge.
  - `E:/wt/kanban-remove-text-pass`
  - `E:/wt/merge-1248-new`
  - An empty `E:/wt/odysseus-names`.
- **Skill:** a duplicate copy of the fable-prompt skill at `E:/hermes/skills/fable-prompt`.
- **Waiting on Andy:** 3 memory batches and 1 memory-deletion batch in `/memory pending`.

## Already fixed

- The dispatcher was paused for about a day. YOUTH resumed it on Nov 10.
- The duplicate lang key (`story.hint.125`) was fixed by #1243.
- #1174, #1175 and #1193 merged.
- The rat crash was fixed by #1211.
