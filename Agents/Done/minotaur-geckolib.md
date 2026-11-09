---
status: done
task: minotaur-geckolib
branch: claude/minotaur-geckolib
with:
pr: 1188
mods: kingdoms
versions:
started: 2026-11-09
owns:
  - kingdoms/art/minotaur.bbmodel
  - kingdoms/art/minotaur-render.png
  - kingdoms/art/minotaur-animations.png
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
PR #1188 moves the minotaur to GeckoLib with calm/hostile idles, walk, charge run, alternating hammer swings and slam, preserving its gameplay stats and AI. Editable CC BY 4.0 source, licence, credit and inspected front/side/back plus animation renders are included. Kingdoms and root builds and PR checks pass; a dedicated dev server reached `Done`. No client visual playtest was performed.
- 2026-11-09 11:49 PR #1188 is merged into master; moved to Agents/Done.
