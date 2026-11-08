# Warped Clam asset credit

The Warped Clam geometry, default Ender texture and animation data are ported from **Inhabitants**:
https://github.com/JeremySeq/Inhabitants

Upstream release: v1.2.1, commit `c3461b5aab57802e83b135010fb3a5e812479c1f`.

- Inhabitants lead coder: **JeremySeq**.
- Warped Clam artist, animator and designer: **Catalyst** (credited in the upstream README).
- Upstream team: **Team Synapse**; the upstream MIT notice names **Team Obsidian**.
- Licence: **MIT**. The unmodified upstream notice is preserved in `warped_clam-LICENSE.txt`.

Source files:

- `src/main/resources/assets/inhabitants/geo/warped_clam.geo.json`
- `src/main/resources/assets/inhabitants/animations/warped_clam.animation.json`
- `src/main/resources/assets/inhabitants/textures/entity/warped_clam/ender.png` (the upstream entity-data default variant)

Port changes for Jaymo's Kingdoms:

- Changed the geometry identifier from `geometry.unknown` to `geometry.warped_clam` and placed the assets under `jaymoskingdoms` in GeckoLib 5.5.7's `geckolib/models/entity/` and `geckolib/animations/entity/` folders.
- Preserved all 5 source bones, 10 cubes, 60 face mappings, pivots, hierarchy and default texture pixels. The zero-height `glowLayer` plane and blocky pearl are intentional source geometry.
- Preserved all 4 source animations exactly: `Opening`, `closing`, `Opened` and `Pushing`, including every rotation/position keyframe and Catmull-Rom interpolation. No animations were added or removed.
- Added `warped_clam.bbmodel`, an editable GeckoLib Blockbench project with the default texture embedded and every bone keyframe.
- Checked the fields against the pack's GeckoLib `kSxHvs99` jar (5.5.7): its 1.12.0 geometry and 1.8.0 animation loaders support the source's per-face UVs, visible bounds, parented bones, loop flag, pre/post vectors and Catmull-Rom keyframes. Added the standard `geckolib_format_version: 2` marker; no format downgrade was needed.
- `warped_clam-render.png` is the visual front/side/back inspection render from `tools/mesh_to_cubes/bbrender.py`. The `Opened` pose was also baked and inspected with `tools/bbmodel_preview.py` to check the jaws, teeth, interior and pearl.
