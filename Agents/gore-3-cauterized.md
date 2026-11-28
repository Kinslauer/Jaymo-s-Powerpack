---
status: in-review
task: gore-3-cauterized
branch: claude/gore-3-cauterized
with: cauterized cut face texture + spark/smoke particle sprites
pr: 2337
mods: jaymospowers
versions: 
started: 2026-11-27
owns:
  - src/main/resources/assets/jaymospowers/textures/entity/wounds/cut_cauterized.png
  - src/main/resources/assets/jaymospowers/textures/particle/air/cauterized_spark_0.png
  - src/main/resources/assets/jaymospowers/textures/particle/air/cauterized_spark_1.png
  - src/main/resources/assets/jaymospowers/textures/particle/air/cauterized_spark_2.png
  - src/main/resources/assets/jaymospowers/textures/particle/air/cauterized_spark_3.png
  - src/main/resources/assets/jaymospowers/textures/particle/air/cauterized_smoke_0.png
  - src/main/resources/assets/jaymospowers/textures/particle/air/cauterized_smoke_1.png
  - src/main/resources/assets/jaymospowers/textures/particle/air/cauterized_smoke_2.png
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
- Delivered the orange-to-black cauterized cut overlay and spark/smoke particle sprites as PR #2337.
- All new particle sprites pass texture_lint.py; the 64x64 overlay matches the existing cut texture.
- Build passes; not tested in game (art-only branch, gameplay wiring separate).
