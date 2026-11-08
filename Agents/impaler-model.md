---
status: running
task: impaler-model
branch: claude/impaler-model
with: 
pr: 
mods: Kingdoms
versions: 
started: 2026-11-08
owns:
  - kingdoms/src/main/resources/assets/jaymoskingdoms/geo/impaler.geo.json
  - kingdoms/src/main/resources/assets/jaymoskingdoms/animations/impaler.animation.json
  - kingdoms/src/main/resources/assets/jaymoskingdoms/textures/entity/impaler.png
  - kingdoms/art/impaler.bbmodel
  - kingdoms/art/impaler-render.png
  - kingdoms/art/impaler-CREDITS.md
  - kingdoms/art/impaler-LICENSE.txt
  - changes/kingdoms/claude-impaler-model.md
---
# Impaler model

**Task:** Port Inhabitants' MIT Impaler model, texture and every source animation to Kingdoms' GeckoLib format. Keep an editable Blockbench source; no gameplay Java.

## Plan
- Inspect and port the original assets with their MIT credit and licence.
- Validate the GeckoLib JSON, render front, side and back views, and inspect them visually.
- Run the full build and PR checks, then open a PR and hand the task to review.

## Notes
- Source: https://github.com/JeremySeq/Inhabitants (JeremySeq / Team Synapse, MIT).
- The Impaler entity and AI are a separate task after the GeckoLib groundwork.

## Done
Work in progress; the assets have not been tested on a live mob in game.
