---
status: in-review
task: atroxiia-geckolib
branch: claude/atroxiia-geckolib
with: 
pr: 1198
mods: kingdoms
versions: 
started: 2026-11-09
owns:
  - kingdoms/art/atroxiia.bbmodel
  - kingdoms/art/atroxiia-*.png
  - kingdoms/art/atroxiia-CREDITS.md
  - kingdoms/art/atroxiia-LICENSE.txt
  - kingdoms/tools/gen_atroxiia_geo.py
  - kingdoms/src/main/resources/assets/jaymoskingdoms/geckolib/models/entity/atroxiia.geo.json
  - kingdoms/src/main/resources/assets/jaymoskingdoms/geckolib/animations/entity/atroxiia.animation.json
  - kingdoms/src/main/resources/assets/jaymoskingdoms/textures/entity/bb/CREDITS.txt (Atroxiia lines only)
  - kingdoms/src/main/java/com/jaymoskingdoms/dragons/AtroxiiaDragon.java
  - kingdoms/src/main/java/com/jaymoskingdoms/dragons/AtroxiiaAnimations.java
  - kingdoms/src/client/java/com/jaymoskingdoms/client/dragons/AtroxiiaDragonModel.java (removed)
  - kingdoms/src/client/java/com/jaymoskingdoms/client/dragons/AtroxiiaDragonRenderer.java
  - kingdoms/src/client/java/com/jaymoskingdoms/client/dragons/DragonsClient.java (Atroxiia registration only)
  - changes/kingdoms/atroxiia-geckolib.md
---
# Atroxiia GeckoLib

**Task:** Rebuild Atroxiia's existing Java-defined lesser dragon as an editable Blockbench model with identical geometry, UVs, hides, markings, scale, seats and hatchling size, then move every old pose and action to GeckoLib animations.

## Plan
- Use `gen_atroxiia.py`, `AtroxiiaDragonModel.setupAnim`, entity hooks and the dragon specs as the specification.
- Generate the model and all keyframes with a repeatable script; keep existing hide/marking textures and original-only provenance.
- Inspect front/right/back/left, every state and old/new comparisons, then wire movement/action controllers and per-frame look/breath/climb posing.
- Build Kingdoms, attempt the requested server boot, run PR checks and hand the PR to review.

## Notes
- Saint's Dragons art is All Rights Reserved and will not be copied.
- No shared `WindDragon.java` or `WindDragonRenderer.java` edits; other dragons are out of scope.

## Done
PR #1198: Atroxiia has an editable 61-bone Blockbench model and 19 GeckoLib animations covering every old state/action, with hides, markings, looks, tack, seats and scales unchanged. Kingdoms and root builds and PR checks pass; the dev server reached `Done` and was stopped. Not tested in game: Atroxiia was not spawned or watched.
