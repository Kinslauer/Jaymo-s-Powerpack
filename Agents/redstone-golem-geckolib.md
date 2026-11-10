---
status: done
task: redstone-golem-geckolib
branch: claude/redstone-golem-geckolib
with: 
pr: 
mods: kingdoms
versions: 
started: 2026-11-09
owns:
  - kingdoms/art/redstone_golem.bbmodel, credits, licence and renders
  - kingdoms/tools/gen_redstone_golem_geo.py
  - kingdoms/src/main/resources/assets/jaymoskingdoms/geckolib/**/redstone_golem.*
  - kingdoms/src/main/resources/assets/jaymoskingdoms/textures/entity/redstone_golem.png
  - kingdoms/src/main/resources/assets/jaymoskingdoms/textures/entity/bb/CREDITS.txt (Redstone Golem lines only)
  - kingdoms/src/main/java/com/jaymoskingdoms/beasts/RedstoneGolem.java (animation controllers and hooks only)
  - kingdoms/src/client/java/com/jaymoskingdoms/client/beasts/BeastsClient.java (Redstone Golem registration and old pose removal only)
  - kingdoms/src/client/java/com/jaymoskingdoms/client/beasts/RedstoneGolemRenderer.java
  - changes/kingdoms/redstone-golem-geckolib.md
---
# Redstone Golem GeckoLib

**Task:** Move the Redstone Golem to GeckoLib with its existing glowing core and idle/walk, replace the code-posed pound and eruption with matching keyframed animations, and add hit flinch and death animations.

## Plan
- Inspect the source rig, old Java poses and the Minotaur GeckoLib conversion.
- Generate an editable Blockbench model and keyframes with a repeatable script; export GeckoLib geometry, animation and texture files.
- Render old/new action poses and front, side and back views; inspect every PNG for missing faces, stray cubes and texture gaps.
- Wire movement and server-triggered action controllers without changing combat behaviour, keep the glowing core, add credits and a change note.
- Build, run PR checks, boot the dev server once, open a PR and hand the task to review.

## Notes
- Source art: polyjo's Redstone Golem (Minecraft Dungeons), Blockbench Workshop, CC BY 4.0; retain credit and licence beside the editable art.
- Only Redstone Golem lines in shared files; the Skulk Golem is a separate task.

## Done
What a player will see, and what wasn't tested.
