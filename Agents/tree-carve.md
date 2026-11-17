---
status: in-review
task: carve the trunk as you chop (from Immersive Tree Chop, MIT)
branch: claude/tree-carve
with: 
pr: 1666
mods: kingdoms
versions: 
started: 2026-11-16
owns:
  - kingdoms/src/main/java/com/jaymoskingdoms/ChoppedLog.java
  - kingdoms/src/main/java/com/jaymoskingdoms/WoodVolume.java
  - kingdoms/src/main/java/com/jaymoskingdoms/Trees.java (chop() only)
  - kingdoms/src/client/java/com/jaymoskingdoms/client/ChoppedLogRenderer.java
---
# tree-carve

**Task:** Andy: "i want the trunk to carve out, retaining the interior texture and all", taking only that from
Immersive Tree Chop (Litewer, MIT). He says our chopped logs show no deepening cracks in game.

## Plan
- Port Immersive Tree Chop's voxel wood volume (16x16x16 per log, carved where the axe lands) and its mesher.
- ChoppedLog's block entity keeps the volume; the renderer draws bark outside and the log's end grain inside the cut.
- Nothing else of the mod (its falling physics, animations, sounds) is taken.

## Notes
- Cloud session (the Claude Code thread Andy opened on 2026-11-07).

## Done
Each axe stroke cuts a bite out of the log where you look, deeper every stroke, bark outside and end grain inside.
Change note `changes/kingdoms/tree-carve.md`. Not built locally (PR checks build it); not played.
