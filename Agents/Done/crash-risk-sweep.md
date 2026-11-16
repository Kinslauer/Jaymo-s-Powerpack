---
status: done
task: Crash-risk review of all three mods (read-only)
branch: claude/chunk-guard-sweep (worktree .claude/worktrees/chunk-guard-sweep)
with: Andy
pr: 1653
mods: powers, kingdoms, kitchen
versions: 
started: 2026-11-16
owns:
  - the 28 Powers files in the fix list (core/Loaded users: Stacked, SkyfallCleanup, OutpostTracker, LabEscape, ... see confirmed.json)
---
# Crash-risk sweep

**Task:** Andy asked for a whole-repo review; scoped to a crash-risk sweep of all three mods.

## Plan
- Triage the Deck crash reports (modpack/deck-crash, modpack/deck-logs): done. 9 own-mod sync-chunk-load hangs, most fixed since (#1435, #1451, #1454, #1459, #1466); 4 third-party (reanimal red panda, MCA spawn queue, Felled CutLogs OOM x2); 1 /forceload hang; 1 AIOOBE with no stack.
- Workflow: Haiku scanners over every server file that reads the world (unguarded files first), looking for sync chunk loads and tick crashes; each finding checked by a Sonnet verifier.
- Phase 2 (offer): static-collection leaks, attachments, packets, codecs.

## Notes
- Guards that count: `Loaded.made(level, pos)` (each mod has one), `getChunkSource().getChunkNow(x, z) != null`. Plain `isLoaded`/`hasChunkAt`/`hasChunk` don't (true while the chunk is still being made).

## Done
- 2026-11-16 12:14 No sign of work for 30 min: marked stalled (whoever ran it stopped).
- 1:40 PM: it was the 5-hour usage limit. All 58 scans done (228 findings), 48 verified (25 real). Resumed to verify the other 180.
- Andy stopped it. 87 of 228 verified: 45 real (25 hang, 2 crash, 18 lag), 42 refuted; 141 unverified. Journal: ~/.claude/projects/E--Origins-Mod/3cb6f135-.../subagents/workflows/wf_ff1eeba0-a36/journal.jsonl (resumable).
- Andy: "start fixes". Fixing the 28 verified Powers sites (18 hang, 2 crash, 8 lag) on claude/chunk-guard-sweep, each fix group independently verified. Kingdoms findings were never verified (scan stopped), so not in this PR.
- PR #1653 open: 25 Powers files (24 fixed + WildRoom). Built, pr_checks pass, not tested in game. Left: Karma portal search, GroundFx light, FutureStart, fireball burst reads. Still unverified: 141 sweep findings (all Kingdoms/Kitchen, some Powers); the known-sites check also flagged LifeStory.reach (no WANTED path), burningVillage box read, and StoryGround checkStructurePresence.
- Andy: "Go" on the Kingdoms pass. Branch claude/kingdoms-chunk-guards (worktree .claude/worktrees/kingdoms-chunk-guards): verifying 175 unverified Kingdoms findings in 138 files + the LifeStory/StoryGround known sites, fixing the real ones, reviewing each fix. PR #1653 (Powers) still open.
- 2026-11-16 17:31 No sign of work for 30 min: marked stalled (whoever ran it stopped).
- 2026-11-16 17:48 PR #1653 is merged into master; moved to Agents/Done.
