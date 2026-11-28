# Cauterized cuts - art for heat blows and the Cauterized gore preset
- Added `assets/jaymospowers/textures/entity/wounds/cut_cauterized.png`, the orange-to-black recolour of the existing open-cut overlay, for use when heat or energy blows cauterize a wound.
- Added `assets/jaymospowers/textures/particle/air/cauterized_spark_0..3.png` and `cauterized_smoke_0..2.png` for the sparks and smoke that replace blood from a cauterized cut.
- All 16x16/9x9 particle sprites pass `texture_lint.py`; the 64x64 overlay matches the existing cut texture size and pattern.
- This PR supplies art only; the gameplay code that selects the cauterized variant for heat blows and the Cauterized preset will wire these assets in.
- Built; not played.
