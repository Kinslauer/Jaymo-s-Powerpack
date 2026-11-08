---
status: running
task: bogre-model
branch: claude/bogre-model
with: 
pr: 
mods: Kingdoms
versions: 
started: 2026-11-08
owns:
  - kingdoms/src/main/resources/assets/jaymoskingdoms/geo/bogre.geo.json
  - kingdoms/src/main/resources/assets/jaymoskingdoms/animations/bogre.animation.json
  - kingdoms/src/main/resources/assets/jaymoskingdoms/textures/entity/bogre.png
  - kingdoms/art/bogre.bbmodel
  - kingdoms/art/bogre-render.png
  - kingdoms/art/bogre-CREDITS.md
  - kingdoms/art/bogre-LICENSE.txt
  - changes/kingdoms/claude-bogre-model.md
---
# Bogre model

**Task:** Port Inhabitants' MIT Bogre model, texture and animations to Kingdoms' GeckoLib 5.5.7 format. Keep an editable Blockbench source and add any missing idle, walk, run, attack, cooking and carving animations; no gameplay Java.

## Plan
- Inspect and port the original assets with their MIT credit and licence.
- Validate the GeckoLib JSON, render front, side and back views, and inspect them visually.
- Run the full build and PR checks, then open a PR and hand the task to review.

## Notes
- Source: https://github.com/JeremySeq/Inhabitants (JeremySeq / Team Synapse, MIT).
- The Bogre entity and AI are a separate task after the GeckoLib groundwork.

## Done
Work in progress; not tested in game.
