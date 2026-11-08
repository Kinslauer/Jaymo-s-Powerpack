---
status: ready
for: prometheus
from: coordinator
asked: 2026-11-08
thread: Claude reviewer session (Andy wants a dedicated modelling model for the Bogre)
claimed_by: hermes-modeller
pr: 1146
suggested_owns:
  - kingdoms/src/main/resources/assets/jaymoskingdoms/geo/bogre.geo.json
  - kingdoms/src/main/resources/assets/jaymoskingdoms/animations/bogre.animation.json
  - kingdoms/src/main/resources/assets/jaymoskingdoms/textures/entity/bogre.png
  - kingdoms/art/bogre.bbmodel (the editable Blockbench source)
---
# Bogre: model, texture and animations (modelling bot)

**Andy's words (2026-11-08):** "pick one that would excel at modelling for the bogre, because claude doesnt do the
best with models."

**What's wanted:** the Bogre's GeckoLib assets for Kingdoms, ready for the Bogre mob task to use. Model and art only:
no Java.
- Start from Inhabitants' own Bogre (MIT, JeremySeq / Team Synapse): its `.geo.json`, `.animation.json` and texture
  from `https://github.com/JeremySeq/Inhabitants` (or the 1.2.1 jar, CurseForge project 1436034). Keep the credit.
- Make them load with the GeckoLib version this pack ships (root `build.gradle`, `maven.modrinth:geckolib:kSxHvs99`):
  check every field against that version's format, and fix anything that changed since their Forge 1.20.1 build.
- Animations the mob will need: idle, walk, run, attack, cooking (serving food) and carving (bone club). Keep the
  ones the source has; add any missing ones in the same style.
- Keep an editable Blockbench copy (`kingdoms/art/bogre.bbmodel`).

**Check your work visually before the PR:** render the model with
`python tools/mesh_to_cubes/bbrender.py kingdoms/art/bogre.bbmodel <out.png>` and look at the PNG with
`vision_analyze`: front, side and back must read as the Bogre, with no missing faces, stray cubes or texture gaps.
Put the render in the PR body.

**PR:** model, texture and animation files plus the render; say what changed from Inhabitants' originals and that
it hasn't been seen in game yet.

## Notes
- The Bogre mob itself (entity, AI, spawning) is a separate task, done after `kingdoms-geckolib-groundwork`.
- 2026-11-08 12:25 Review (head 0281e67): pass. Art only: Inhabitants' Bogre geo (1.12.0, geometry.bogre), 128x128 texture and 21 animations incl. idle/walk/run/attack/cooking/carving, editable .bbmodel, render checked, MIT notice and credit kept; checks green. Not loaded on a mob or seen in game (the Bogre mob task wires it up).
- 2026-11-08 12:26 Reviewed; Andy allowed sending it to the coordinator.
