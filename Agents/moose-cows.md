---
status: running
task: Cube Animals moose as cows beside our bull
branch: claude/project-thread-dhegwo
with: Claude (cloud thread "Add Cube moose as cows")
pr: 
mods: kingdoms
versions: 
started: 2026-11-14
owns:
  - kingdoms/src/main/java/com/jaymoskingdoms/wild/Moose.java
  - changes/kingdoms/moose-cows.md
---
# Moose cows

**Task:** Andy: "can we have both? as breeds? or maybe keep our bull and the cube moose are cows?" Our moose stays the bull; Cube Animals' moose come with it as antlerless cows.

## Plan
- A naturally spawned bull brings 1-2 Cube moose (antler scale -1), and walks back to its nearest cow when she strays.

## Notes
- Cube's `MooseEntity.setAntlersScale(float)` is added to 1 by its model; called by reflection (no compile dependency).
- Lore: moose are already in Lore.md's Beasts paragraph; no new placement.

## Done
