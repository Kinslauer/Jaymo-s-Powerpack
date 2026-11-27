---
status: running
task: gore-3-cauterized
branch: claude/gore-3-cauterized
with: cauterized cut face texture + spark/smoke particle sprites
pr: 
mods: jaymospowers
versions: 
started: 2026-11-27
owns:
  - src/main/resources/assets/jaymospowers/textures/particle/cauterized_spark.png
  - src/main/resources/assets/jaymospowers/textures/particle/cauterized_smoke.png
  - src/main/resources/assets/jaymospowers/textures/entity/cut_face_cauterized.png
  - src/main/resources/assets/jaymospowers/models/entity/cut_face_cauterized.bbmodel
---
# Cauterized cuts: art for heat blows and the Cauterized gore preset

**Task:** Provide the orange-to-black cauterized cut-face texture and spark/smoke particle sprites for the gore preset / per-heat-blow look.

## Plan
- Find existing cut-face / blood decal textures in Powers to use as shape reference.
- Recolour / repaint a cauterized variant: hot orange edges, cooling black seams, no blood red.
- Draw 16x16 spark and smoke particle sprites in Ink Studio style (limited palette, no pixel blood).
- Run `texture_lint.py` on every new PNG and fix CHECK lines.
- Build-check `sh ./gradlew build` + `python tools/pr_checks.py`.
- Push and open PR; set task `status=in-review`.

## Notes
- Modeller-only: no Java gameplay code; this branch supplies assets the gameplay PR will wire.

## Done
