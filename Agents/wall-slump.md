---
status: in-review
task: Ragdolls knocked into walls slide down and sit against them (not all lying flat)
branch: claude/project-thread-7ttuy5
with: Claude (cloud thread "Slump ragdolls against walls")
pr: 1601
mods: kitchen, powers
versions: 
started: 2026-11-15
owns:
  - kitchen/src/main/java/com/jaymoskitchen/ragdoll/WallSlump.java
  - kitchen/src/main/java/com/jaymoskitchen/corpse/CorpseRagdoll.java
  - kitchen/src/main/java/com/jaymoskitchen/corpse/CorpseImpacts.java
  - kitchen/src/main/java/com/jaymoskitchen/corpse/CorpseEntity.java (slumpWall, stillPropped, Propped save)
  - src/main/java/com/jaymospowers/core/BladeBlood.java (wallSmear)
  - src/main/java/com/jaymospowers/power/web/KitchenCompat.java (onSlump hook)
---
# Wall slump

**Task:** Andy: "can enemies who ragdoll into walls pose similarly? as if they were knocked into the wall and slipped down onto the floor? basically so they dont all default to lying flat."

## Plan
- Kitchen WallSlump: humanoid corpse that strikes a wall (thrown, or killed beside it) slides down and sits, back to the wall, legs out, slumped to one side, head lolled; stays until disturbed.
- Powers: blood smear down the wall + stain where it sits.

## Notes
- Checked in tools/ragdoll_sim (new section).

## Done
PR #1601. Humanoid corpses flung into a wall (or killed beside it) slide down and sit, back to the wall, until disturbed; blood smear down the wall. Kitchen + Powers compile with Gradle; ragdoll_sim passes; not played.
