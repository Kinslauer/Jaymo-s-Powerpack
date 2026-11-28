---
status: stalled
task: gore-2-cut-faces
branch: t3/gore-2-cut-faces
with: 
pr: 
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
