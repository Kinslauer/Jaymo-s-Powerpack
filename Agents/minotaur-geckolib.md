---
status: running
task: minotaur-geckolib
branch: claude/minotaur-geckolib
with:
pr:
mods: kingdoms
versions:
started: 2026-11-09
owns:
  - kingdoms/art/minotaur.bbmodel
  - kingdoms/art/minotaur-CREDITS.md
  - kingdoms/art/minotaur-LICENSE.txt
  - kingdoms/src/main/resources/assets/jaymoskingdoms/geckolib/models/entity/minotaur.geo.json
  - kingdoms/src/main/resources/assets/jaymoskingdoms/geckolib/animations/entity/minotaur.animation.json
  - kingdoms/src/main/resources/assets/jaymoskingdoms/textures/entity/minotaur.png
  - kingdoms/src/main/java/com/jaymoskingdoms/beasts/Minotaur.java
  - kingdoms/src/client/java/com/jaymoskingdoms/client/beasts/BeastsClient.java (minotaur registration only)
  - kingdoms/src/main/resources/assets/jaymoskingdoms/bbmodels/minotaur.json
  - kingdoms/src/main/resources/assets/jaymoskingdoms/textures/entity/bb/minotaur.png
  - kingdoms/src/main/resources/assets/jaymoskingdoms/textures/entity/bb/CREDITS.txt (minotaur lines only)
  - changes/kingdoms/claude-minotaur-geckolib.md
---
# Minotaur GeckoLib

**Task:** Move the existing Kingdoms minotaur to GeckoLib, retaining its appearance and stats while using its walk, idle, run and attack animations.

## Plan
- Preserve the editable CC BY 4.0 Blockbench source, credit and licence.
- Convert and visually check the model, texture and animations.
- Wire the existing mob and renderer to GeckoLib without changing gameplay behaviour.
- Build, run PR checks, and hand the PR to review.

## Notes
- Source: “Minotaur” by eanncrod, Blockbench Workshop, CC BY 4.0.

## Done
Work in progress; not yet tested in game.
