---
status: in-review
task: android-mech-geckolib
branch: claude/android-mech-geckolib
with: 
pr: 1195
mods: powers
versions: 
started: 2026-11-09
owns:
  - tools/blockbench/android_mech/*.bbmodel
  - art/android_mech.bbmodel
  - art/android_mech_*.bbmodel
  - art/android_mech*.png
  - art/android_mech-CREDITS.md
  - art/android_mech-LICENSE.txt
  - tools/gen_android_mech_gecko.py
  - build.gradle (GeckoLib compile dependency only)
  - src/main/resources/fabric.mod.json (GeckoLib dependency only)
  - src/main/resources/assets/jaymospowers/geckolib/** (android mech files)
  - src/main/resources/assets/jaymospowers/textures/entity/bb/CREDITS.txt
  - src/main/java/com/jaymospowers/power/chrono/future/FutureMech.java
  - src/main/java/com/jaymospowers/power/chrono/future/FutureGuns.java (built-in cannon shot result only)
  - src/client/java/com/jaymospowers/client/power/chrono/AndroidMechModel.java
  - src/client/java/com/jaymospowers/client/power/chrono/FutureMechRenderer.java
  - changes/powers/android-mech-geckolib.md
---
# Android mech GeckoLib

**Task:** Put the android war mech and test unit, in all four builds, on GeckoLib with editable Blockbench sources and idle, walk, attacks, power transitions, hit flinch and death animations while preserving the renderer's visor, core and lights.

## Plan
- Inspect the four existing models, old Java poses, entity hooks and renderer effects.
- Generate shared-skeleton keyframed animations with a repeatable script, export GeckoLib assets, and preserve credits.
- Render and inspect front, side, back and old/new pose comparisons; wire controllers without changing gameplay behaviour.
- Build, boot the dev server once, run PR checks, then hand the PR to review.

## Notes
- Original existing models are the source; no outside art is planned.
- Four builds should share one animation set after bone names are aligned.

## Done
PR #1195: all four android mech builds share an editable 33-bone GeckoLib rig with idle, old-pose walk/aim, cannon, shell, smash, hurt and held death animations; original skins, optional equipment and glow are preserved. Full build and PR checks pass; generator output is reproducible and all renders were inspected. Not tested in game: two dev-server attempts stopped before world load on unrelated missing Fabric API/Resourceful Lib dependencies.
