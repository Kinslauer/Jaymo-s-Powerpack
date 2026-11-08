---
status: open
for: prometheus
from: coordinator
asked: 2026-11-08
thread: Claude reviewer session (Andy: "have it do the impaler and warped clam")
claimed_by: 
pr: 
suggested_owns:
  - kingdoms/src/main/resources/assets/jaymoskingdoms/geo/impaler.geo.json
  - kingdoms/src/main/resources/assets/jaymoskingdoms/animations/impaler.animation.json
  - kingdoms/src/main/resources/assets/jaymoskingdoms/textures/entity/impaler.png
  - kingdoms/art/impaler.bbmodel (the editable Blockbench source)
---
# Impaler: model, texture and animations (modelling bot)

**Andy's words (2026-11-08):** "have it do the impaler and warped clam" (after the Bogre model).

**What's wanted:** the Impaler's GeckoLib assets for Kingdoms, ready for the Impaler mob task to use. Model and art only:
no Java.
- Start from Inhabitants' own Impaler (MIT, JeremySeq / Team Synapse) (find its file names in the source): its `.geo.json`, `.animation.json` and texture
  from `https://github.com/JeremySeq/Inhabitants` (or the 1.2.1 jar, CurseForge project 1436034). Keep the credit.
- Make them load with the GeckoLib version this pack ships (root `build.gradle`, `maven.modrinth:geckolib:kSxHvs99`):
  check every field against that version's format, and fix anything that changed since their Forge 1.20.1 build.
- Animations: keep every one the source has (idle, walk, attack and the rest); add none unless one is clearly missing for a mob of this kind.
- Keep an editable Blockbench copy (`kingdoms/art/impaler.bbmodel`).

**Check your work visually before the PR:** render the model with
`python tools/mesh_to_cubes/bbrender.py kingdoms/art/impaler.bbmodel <out.png>` and look at the PNG with
`vision_analyze`: front, side and back must read as the Impaler, with no missing faces, stray cubes or texture gaps.
Put the render in the PR body.

**PR:** model, texture and animation files plus the render; say what changed from Inhabitants' originals and that
it hasn't been seen in game yet.

## Notes
- The Impaler mob itself (entity, AI, spawning) is a separate task.
