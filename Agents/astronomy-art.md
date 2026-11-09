---
status: running
task: astronomy-art
branch: claude/astronomy-art
with:
pr:
mods: kingdoms
versions:
started: 2026-11-08
owns:
  - art/astronomy/
  - kingdoms/src/main/resources/assets/jaymoskingdoms/textures/block/telescope.png
  - kingdoms/src/main/resources/assets/jaymoskingdoms/textures/item/telescope.png
  - kingdoms/src/main/resources/assets/jaymoskingdoms/models/block/telescope.json
  - kingdoms/src/main/resources/assets/jaymoskingdoms/models/item/telescope.json
  - kingdoms/src/main/resources/assets/jaymoskingdoms/items/telescope.json
  - changes/kingdoms/astronomy-art.md
---
# Astronomy art

**Task:** Establish the original high-Renaissance study style for the astronomy set and make the first asset set: a wooden, brass-bound telescope with a jointed tripod, an item icon, editable Blockbench source and inspected renders. Art only; no gameplay Java.

## Plan
- Define one sepia ink, walnut and aged-brass palette from Andy's reference without copying its artwork.
- Build the telescope as a readable placed model with a long wooden tube, brass fittings, visible glass and jointed tripod.
- Keep editable sources and front/side/back renders under `art/astronomy/`; validate exported models and textures.
- Build Kingdoms, run PR checks, open the PR and hand the task to review.

## Notes
- Original art only; no Astronomy mod artwork used.
- Later astronomy code tasks own registration, aiming, observation rules and animations; this PR supplies only the first telescope art and shared palette guidance.

## Done
Work in progress; not tested in game.
