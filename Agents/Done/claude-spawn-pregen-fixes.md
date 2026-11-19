---
status: done
task: spawn-pregen-fixes
branch: claude/spawn-pregen-fixes
with:
pr: 1681
mods: [kingdoms]
versions:
started: 2026-11-17
owns:
  - kingdoms/src/main/java/com/jaymoskingdoms/KeepingCells.java
  - kingdoms/src/main/java/com/jaymoskingdoms/LifeStory.java
  - kingdoms/src/main/java/com/jaymoskingdoms/Pregen.java
---
# Spawn and pregen fixes

**Task:** Andy: "do both fixes", after the Deck's 7:18 AM watchdog crash and the lag from starts in ungenerated land.

## Plan
- KeepingCells never reads unloaded chunks (look waits for the land round a fresh villager; hold skips while the cell's edge is unloaded).
- Random start origins and start searches stay inside the land Chunky has made (Pregen reads Chunky's saved task).

## Notes
- The Deck's Chunky task: square, radius 1000 round 0,0, ~16,080 chunks; origins reached 1,500.
- Not covered: StoryGround harbour/milestone searches and ArrivalGround's 20-chunk make-ahead near the edge.

## Done
Players set out inside pre-generated land; the smith's keeping can't force a chunk load. CI build only; not tested on the server yet.
- 2026-11-17 08:15 PR #1681 is merged into master; moved to Agents/Done.
