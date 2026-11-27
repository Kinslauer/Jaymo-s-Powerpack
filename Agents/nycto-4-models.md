---
status: running
task: nycto-4-models
branch: claude/nycto-4-models
with:
pr:
mods: kingdoms
versions:
started: 2026-11-27
owns:
  - kingdoms/art/night_horror.bbmodel
  - kingdoms/tools/gen_night_horror.py
  - kingdoms/src/main/resources/assets/jaymoskingdoms/geckolib/models/entity/night_horror.geo.json
  - kingdoms/src/main/resources/assets/jaymoskingdoms/geckolib/animations/entity/night_horror.animation.json
  - kingdoms/src/main/resources/assets/jaymoskingdoms/textures/entity/night_horror*.png
---
# Night's Blood 4 models: the true shape of the night

**Task:** Build the vanilla-style gaunt winged horror model and textures for the vampire player's fifth shape, with idle, walk, leap and strike animations.

## Plan
- Reuse Antigravity's uncommitted start if it helps (T3 worktree has night_horror.bbmodel, gen script and previews).
- Run/refine the model and texture generation, check previews with vision_analyze and texture_lint.
- Export GeckoLib geo/animation/texture files into kingdoms resources.
- Build kingdoms, open PR, attach preview, set in-review.

## Notes
- 2026-11-27 09:35 Claimed by hermes-modeller after Antigravity quota was reached.

## Done
