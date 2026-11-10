---
status: done
task: omen-geckolib
branch: claude/omen-geckolib
with: 
pr: 
mods: powers
versions: 
started: 2026-11-09
owns:
  - art/the_omen_red.bbmodel, art/the_omen_blue.bbmodel and their credits, licences and renders
  - tools/gen_omen_geckolib.py
  - src/main/resources/assets/jaymospowers/geckolib/** (Omen files only)
  - src/main/resources/assets/jaymospowers/textures/entity/bb/CREDITS.txt (Omen lines only)
  - src/main/java/com/jaymospowers/power/chrono/future/FutureOmen.java
  - src/main/java/com/jaymospowers/power/chrono/future/OmenAnimations.java
  - src/client/java/com/jaymospowers/client/power/chrono/OmenRenderer.java
  - build.gradle and src/main/resources/fabric.mod.json (GeckoLib dependency only, if needed)
  - changes/powers/omen-geckolib.md
---
# Omen GeckoLib

**Task:** Move the red and blue Omen mechs from code-posed Blockbench models to GeckoLib, preserving their existing poses and adding idle, walk, run, turning, cannon and melee actions, hit flinch and death animations.

## Plan
- Inspect both rigs and every existing entity/renderer pose, then generate editable Blockbench keyframes with a repeatable script.
- Export GeckoLib geometry and animations, render old/new and front/side/back comparisons, and inspect the PNGs.
- Wire movement and action controllers without changing gameplay behaviour; keep head look and aiming aligned with existing state.
- Add provenance, licence, change note and renders; build, run PR checks, attempt the requested server boot, and hand the PR to review.

## Notes
- Source art is Blockbench Workshop, CC BY 4.0; retain the existing author credit and licence beside each editable model.
- Red and blue will share animation timing where their rigs match, while keeping their own geometry and textures.

## Done
What a player will see, and what wasn't tested.
