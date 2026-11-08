---
status: done
task: warped-clam-model
branch: claude/warped-clam-model
with: 
pr: 1153
mods: Kingdoms
versions: 
started: 2026-11-08
owns:
  - kingdoms/src/main/resources/assets/jaymoskingdoms/geckolib/models/entity/warped_clam.geo.json
  - kingdoms/src/main/resources/assets/jaymoskingdoms/geckolib/animations/entity/warped_clam.animation.json
  - kingdoms/src/main/resources/assets/jaymoskingdoms/textures/entity/warped_clam.png
  - kingdoms/art/warped_clam.bbmodel
  - kingdoms/art/warped_clam-render.png
  - kingdoms/art/warped_clam-CREDITS.md
  - kingdoms/art/warped_clam-LICENSE.txt
  - changes/kingdoms/claude-warped-clam-model.md
---
# Warped Clam model

**Task:** Port Inhabitants' MIT Warped Clam model, texture and every source animation to Kingdoms' GeckoLib 5.5.7 format. Keep an editable Blockbench source; no gameplay Java.

## Plan
- Inspect and port the original assets with their MIT credit and licence.
- Validate the GeckoLib JSON, render front, side and back views, and inspect them visually.
- Run the full build and PR checks, then open a PR and hand the task to review.

## Notes
- Source: https://github.com/JeremySeq/Inhabitants (JeremySeq / Team Synapse, MIT), version 1.2.1.
- The Warped Clam entity and AI are a separate task after the GeckoLib groundwork.

## Done
Added the Warped Clam's GeckoLib geometry, unchanged Ender-variant texture, 4 source animations, editable Blockbench project, render and MIT credit as PR #1153. Root and Kingdoms builds and PR checks passed; closed and opened front/side/rear views were inspected. No gameplay Java was added and the assets were not tested on a live mob in game.
- 2026-11-08 21:54 PR #1153 is merged into master; moved to Agents/Done.
