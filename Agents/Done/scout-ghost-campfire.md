---
status: done
task: Bring in Andy's three Blockbench models (goblin scout, ghost, camping campfire)
branch: claude/project-thread-q2zruj
with: 
pr: 1390
mods: kingdoms, kitchen, powers
versions: 
started: 2026-11-13
owns:
  - kingdoms/src/main/java/com/jaymoskingdoms/GoblinScout.java
  - kingdoms/src/main/java/com/jaymoskingdoms/GraveGhost.java
  - kitchen/src/main/resources/assets/jaymoskitchen/models/block/spit*.json
---
# Scout, ghost and campfire models

**Task:** Andy uploaded goblin-scout, ghost and camping-campfire .bbmodel files with no text. Proposed: scout = its own
creature at goblin villages, ghost = the newly buried by their gravestone at night, campfire = the look of Kitchen's spit.
Andy picked "Build all three as proposed".

## Plan
- Bake the three models, copy sources to tools/bbmodels.
- Kingdoms: GoblinScout, GraveGhost. Kitchen: spit look.

## Notes
- 

## Done
Goblin scouts range round goblin villages and cackle the village onto prey; ghosts stand at fresh graves for three
nights; the spit is a frame of posts. Compiled (Kingdoms, Kitchen), PR checks pass, not played. Open: model credits,
two (new) Lore.md paragraphs.

Follow-up (Andy): the ghost is also how a player looks in Limbo, with a ghost's voice. Limbo = the button on the final
death screen (PR #1387), so this waits for that merge. A first version tied to lying left for dead was built and reverted
on this branch (commit 11474b03: `LimboGhost` entity and renderer, `VoiceMasks.Mask.GHOST`, `VoiceRange.spoken` are
reusable; its `Limbo` class name collides with #1387's).

Andy then kept the left-for-dead ghosts: back on this branch as Powers `core/wounds/OutOfBody` + `PlayerGhost`
(`client/wounds/OutOfBodyClient`, `PlayerGhostRenderer`) and the Kingdoms ghost voice. Still to do after #1387: the same
ghost and voice for a player in Limbo.
- 2026-11-13 02:37 PR #1390 is merged into master; moved to Agents/Done.
