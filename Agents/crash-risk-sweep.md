---
status: stopped
task: Crash-risk review of all three mods (read-only)
branch: none (review only, no edits)
with: Andy
pr: 
mods: powers, kingdoms, kitchen
versions: 
started: 2026-11-16
owns:
  - (nothing: read-only)
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
