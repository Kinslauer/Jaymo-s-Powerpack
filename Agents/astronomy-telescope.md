---
status: blocked
task: astronomy-telescope
branch: claude/astronomy-telescope
with:
pr:
mods: kingdoms
versions:
started: 2026-11-09
owns: []
---
# Astronomy telescope

**Task:** Supply the placeable, aimable telescope art and animation support; gameplay registration and observation rules remain with the gameplay implementation.

## Plan
- Reuse the original telescope art in PR #1185 after it merges rather than duplicating the same model in a second branch.
- Convert it to an articulated rig only if Andy confirms that route for continuous per-telescope azimuth and elevation.

## Notes
- PR #1185 now owns the static block/item models, textures and editable telescope source.
- This branch contains no edits; it is waiting on the base art and the rig decision recorded in the Inbox task.

## Done
Blocked before editing. No uncommitted work; nothing was tested in game.
