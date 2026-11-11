---
status: merged
task: imps-nether
branch: claude/project-thread-2cvsj6
with: 
pr: 1286
mods: kingdoms
versions: 
started: 2026-11-11
owns:
  - kingdoms/tools/gen_imp.py
  - kingdoms/art/imp*
  - kingdoms/src/main/java/com/jaymoskingdoms/nether/Imp.java, ImpFireball.java, Imps.java
  - kingdoms/src/client/java/com/jaymoskingdoms/client/nether/ImpRenderer.java
  - kingdoms/src/main/resources/assets/jaymoskingdoms/geckolib/**/imp.*
  - kingdoms/src/main/resources/assets/jaymoskingdoms/textures/entity/imp.png, zombified_imp.png
  - docs/Imp-Model-Spec.md
---
# Nether imps

**Task:** Andy (2026-11-11): flying Nether imps of a piglin-like make, zombified ones too, lunging from many angles, a 3 s charged fireball a well-timed hit sets off early (killing the imp), fireballs set trees and surroundings ablaze. Full GeckoLib model, every part animated (arms, legs, wings, fireball, swoop, basic swing).

## Plan
- Model, textures and animations from kingdoms/tools/gen_imp.py (our own, REA spec in docs/Imp-Model-Spec.md).
- nether/Imp.java (both kinds), ImpFireball.java, registration in Imps.java called from Nether.register().

## Notes
- The Long Defence film thread adds the imps to the film after the merge (it has LoreFilmClient.igniteNear in PR #1268).

## Done
PR #1286 (change note changes/kingdoms/project-thread-2cvsj6.md). A player sees imps and zombified imps flying in the Nether, lunging from new sides, charging a 3 s fireball a blow in the last 0.6 s sets off, and fireballs lighting trees. Built; on a dev server imps fought a golem, threw fireballs, lit fires and went off when struck in the opening. Not seen in a client (animations in motion, spawn rates).
