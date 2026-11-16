---
status: done
task: Deck server handoff (keep the Steam Deck server stable)
branch: claude/capital-search-no-chunk-wait
with: Claude Code on Andy's PC
pr: 1619
mods: kingdoms
versions: 
started: 2026-11-15
owns:
  - kingdoms/src/main/java/com/jaymoskingdoms/StoryGround.java
  - changes/kingdoms/capital-search-no-chunk-wait.md
---
# Deck server watch

**Task:** pick up "Deck server handoff.md": watch the Deck server, fix each crash on a PR, redeploy keeping the world,
until it runs 30 minutes with Andy on and no crash. History goes in `Agents/Done/power-pack-admin.md`.

## Plan
- Fix the 8:15 PM watchdog kill (Kingdoms' first-capital village search made chunks on the server thread).
- Find what held the server thread for 44 s before it (JFR armed on the server).
- Merge, deploy keeping the world, retest.

## Notes
- 8:08 PM: someone deployed a NEW world (Powers 0.24.22, Kingdoms 0.9.53) with a Chunky 2500 pregen. The 5:45 PM
  world is now `world-old-20261115-200851`, so the handoff's 16 chunks without terrain are in that old world, not this one.
- 8:15 PM watchdog kill. Manigraphic78 was joining (authenticated 8:14:16, placed 8:15:39). The stuck tick began
  ~8:14:42; the server thread printed nothing from 8:14:49 to 8:15:33 (cause unknown: the dump was taken after).
  Then the join started the first capital's village search, whose `level.getChunk(STRUCTURE_STARTS)` waited behind
  Chunky (4.6 s, "8 of 25 places"), and the watchdog fired during it.
- Not memory: heap 2.5 of 8 GB used at the crash.
- 8:24 PM: server restarted by hand. Chunky's task was lost in the kill; ChunkyCompat ran `chunky continue` anyway.
- 8:30 PM: JFR recording "stall" on the running server (maxage 15 min, dumps to `logs/stall.jfr` on exit). It's lost
  on the next restart unless re-armed.

## Done
- 2026-11-15 20:57 PR #1619 is merged into master; moved to Agents/Done.
