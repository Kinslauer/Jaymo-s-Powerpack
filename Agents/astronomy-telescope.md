---
status: running
task: astronomy-telescope
branch: claude/astronomy-telescope
with:
pr:
mods: kingdoms
versions:
started: 2026-11-09
owns:
  - art/astronomy/telescope.bbmodel
  - art/astronomy/generate_telescope.py
  - art/astronomy/README.md
  - art/astronomy/telescope_preview.png
  - art/astronomy/telescope_rig_preview.png
  - kingdoms/src/main/resources/assets/jaymoskingdoms/geckolib/models/block/telescope.geo.json
  - kingdoms/src/main/resources/assets/jaymoskingdoms/geckolib/animations/block/telescope.animation.json
  - changes/kingdoms/claude-astronomy-telescope.md
---
# Astronomy telescope

**Task:** Supply the placeable, aimable telescope art and animation support; gameplay registration and observation rules remain with the gameplay implementation.

## Plan
- Reuse the original telescope art merged in PR #1185.
- Convert it to a centred GeckoLib block rig with fixed `base`, yawing `azimuth` and tilting `elevation` bones.
- Add editable idle/aim/reference animations, export the block geometry and animation JSON, and inspect front/side/back plus posed renders.
- Keep registration, saved aim, skills and observation rules for the gameplay implementation; no gameplay Java in this pass.

## Notes
- PR #1185 has merged, and Andy confirmed GeckoLib on 2026-09-09.
- One walnut/brass telescope is enough for this pass; the Altazimuth Mount can use the same rig and its physical scales rather than duplicating tiers.
- `astronomy-sky` and observation behaviour remain separate dependencies.

## Done
Work in progress; not yet tested in game.
