---
status: in-review
task: gore-2-cut-faces
branch: t3/gore-2-cut-faces
with: 
pr: 2358
mods: jaymospowers
versions: 
started: 2026-11-27
owns:
  - src/client/java/com/jaymospowers/client/Dismembered.java
  - src/client/java/com/jaymospowers/client/SeveredPartRenderer.java
  - src/main/resources/assets/jaymospowers/textures/entity/wounds/cut_face_*.png
  - tools/gen_cut_faces.py
  - changes/powers/t3-gore-2-cut-faces.md
---
# Cut faces with insides

**Task:** Show vanilla-style cross-section cut faces (meat, bone ring, torso organ, or the body's own material: bone, stone, iron, slime, etc.) on severed stumps, both ends of a `SeveredPart`, the top half of a waist halving, and Kitchen corpses, at every blood setting except Off.

## Plan
- Paint clean vanilla-style 16x16 cross-section textures (`tools/gen_cut_faces.py` -> `textures/entity/wounds/cut_face_*.png`) following `docs/Vanilla-Style.md` (discrete shade ramps, light from top-left, no noise or soft gradients, no pixel splats).
- Draw cross-section faces on living body stumps, headless necks, waist halvings, `SeveredPart` cut ends, and Kitchen corpses (`CorpseDressing`) at every blood setting except Off (and hidden under Streamer safe), matched to each body's material (`BladeBlood`, tags, heritages).
- Verify with `sh ./gradlew build` and `python tools/pr_checks.py`.

## Notes
- Claimed and actively being built by Antigravity in T3 Code (`t3`).

## Done
- 2026-11-27 21:51 No sign of work for 30 min: marked stalled (whoever ran it stopped).
- 2026-11-28 3:45 AM Resumed after the quota stop; work was already written but unbuilt. Build green, pr_checks passed, PR #2358 opened (head b32215e0), task to in-review.
- 2026-11-28 3:45 AM `sh ./gradlew build` BUILD SUCCESSFUL; `python tools/pr_checks.py` passed (40 files vs origin/master).
- 2026-11-28 3:45 AM `texture_lint.py` on all 36 cut-face PNGs: 25 clean, 11 CHECK lines explained in the PR (concentric rings on metal/magma/wood read as banding; the meat-bone edge reads as jaggies).
- 2026-11-28 3:45 AM Not tested in game. Stoneborn and snow golem cuts are flat clustered rock/snow (a solid has no bone ring): ask Andy to look in game.
