---
status: done
task: aetherion-geckolib
branch: claude/aetherion-geckolib
with:
pr: 1194
mods: kingdoms
versions:
started: 2026-11-09
owns:
  - kingdoms/art/aetherion.bbmodel
  - kingdoms/art/aetherion-render.png
  - kingdoms/art/aetherion-animations.png
  - kingdoms/art/aetherion-CREDITS.md
  - kingdoms/art/aetherion-LICENSE.txt
  - kingdoms/tools/gen_aetherion_geo.py
  - kingdoms/src/main/resources/assets/jaymoskingdoms/geckolib/models/entity/aetherion.geo.json
  - kingdoms/src/main/resources/assets/jaymoskingdoms/geckolib/animations/entity/aetherion.animation.json
  - kingdoms/src/main/java/com/jaymoskingdoms/dragons/AetherionDragon.java
  - kingdoms/src/main/java/com/jaymoskingdoms/dragons/AetherionAnimations.java
  - kingdoms/src/client/java/com/jaymoskingdoms/client/dragons/AetherionDragonModel.java (removed)
  - kingdoms/src/client/java/com/jaymoskingdoms/client/dragons/AetherionDragonRenderer.java
  - kingdoms/src/client/java/com/jaymoskingdoms/client/dragons/DragonsClient.java (Aetherion registration only)
  - kingdoms/src/main/resources/assets/jaymoskingdoms/textures/entity/bb/CREDITS.txt (Aetherion lines only)
  - changes/kingdoms/aetherion-geckolib.md
---
# Aetherion GeckoLib

**Task:** Rebuild Aetherion's existing Java-defined star dragon as an editable Blockbench model with the same geometry, UVs, hides, markings, size and seats, then move every existing pose and action to GeckoLib animations.

## Plan
- Use `AetherionDragonModel.setupAnim`, `AetherionDragon` and `gen_aetherion.py` as the geometry and animation specification.
- Generate the `.bbmodel`, keyframes and GeckoLib files with a repeatable script; retain original art credit and licence notes.
- Render old and new poses side by side, inspect front/side/back and every action, then wire movement/action controllers and per-frame head/jaw posing.
- Build Kingdoms and the root, boot a dedicated dev server once, run PR checks and hand the PR to review.

## Notes
- Original model and animation work only; Saint's Dragons art is All Rights Reserved and will not be copied.
- Other dragons and shared WindDragon files are out of scope.

## Done
PR #1194 supplies Aetherion's editable 88-cube Blockbench model, unchanged hide/marking UVs, ten GeckoLib animations, movement/action controllers and a renderer preserving looks, scale, seats and per-frame head/jaw/flight posing. Geometry has zero mismatched source vertices; four-view and eleven old/new pose renders were inspected. Kingdoms clean build, root build and PR checks pass. A dev server boot was attempted but did not reach `Done` because concurrent Gradle activity deleted shared build output; no client playtest was performed.
- 2026-11-09 13:16 PR #1194 is merged into master; moved to Agents/Done.
