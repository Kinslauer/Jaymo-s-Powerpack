---
status: done
task: nycto-4-models
branch: claude/nycto-4-models
with:
pr: 2302
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
- 2026-11-27 09:58 Andy wants bigger wings (his steer to Antigravity at 8:59, just before its quota ran out). Antigravity never finished it: its last step said the wings were still too small, then the quota hit. The copied geo still has the old wing sizes (inner membrane 9x11.8, outer 12x14.85). Make the folded membrane wings clearly larger and re-check the previews. (Added by Claude for Andy.)
- 2026-11-27 10:48 Addressed Andy's bigger-wings note: inner span 9→11, outer 12→15, taller membranes; removed wrist-claw/outer finger-bone cubes that looked like stray geometry in rest pose. PR #2302 updated.

## Done
- 2026-11-27 16:22 No sign of work for 30 min: marked stalled (whoever ran it stopped).
- 2026-11-27 17:17 PR #2302 is merged into master; moved to Agents/Done.
