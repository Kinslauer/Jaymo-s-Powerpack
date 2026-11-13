---
status: in-review
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
  - src/main/java/com/jaymospowers/core/wounds/Limbo.java
  - src/main/java/com/jaymospowers/core/wounds/LimboGhost.java
  - src/client/java/com/jaymospowers/client/wounds/Limbo*.java
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

Follow-up (Andy): players left for dead are out of their body as the ghost (Powers `Limbo`), with a ghost's voice from
where the ghost is (Kingdoms `VoiceMasks.GHOST`, `VoiceRange.spoken`). Compiled, not played. Open: "(new) Out of the body"
in Lore.md, makers' names for the credits.
