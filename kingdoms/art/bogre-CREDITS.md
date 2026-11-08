# Bogre asset credit

The Bogre geometry, texture and animation data are ported from **Inhabitants**:
https://github.com/JeremySeq/Inhabitants

Upstream release: v1.2.1, commit `c3461b5aab57802e83b135010fb3a5e812479c1f`.

- Inhabitants lead coder: **JeremySeq**.
- Bogre artist, animator and designer: **Catalyst** (credited in the upstream README).
- Upstream team: **Team Synapse**; the upstream MIT notice names **Team Obsidian**.
- Licence: **MIT**. The unmodified upstream notice is preserved in `bogre-LICENSE.txt`.

Source files:

- `src/main/resources/assets/inhabitants/geo/bogre.geo.json`
- `src/main/resources/assets/inhabitants/animations/bogre.animation.json`
- `src/main/resources/assets/inhabitants/textures/entity/bogre.png`

Port changes for Jaymo's Kingdoms:

- Changed the geometry identifier to `geometry.bogre` and placed assets under `jaymoskingdoms`.
- Added empty `attackPoint` and `halo_outline` bones because the source animation tracks target them but the source geometry omitted them. They add no visible cubes.
- Preserved all 40 source cubes, the base texture pixels, and all 21 source animations, including their Catmull-Rom keyframes and sound, particle and instruction markers. Required idle, walk, run, attack, cooking and carving tracks were already present.
- Added `bogre.bbmodel`, an editable GeckoLib Blockbench project with the texture embedded and all bone keyframes.
- Checked the fields against GeckoLib 5.5.7's 1.12.0 geometry and 1.8.0 animation parsers; the source formats are supported without downgrading.
- `bogre-render.png` is the visual front/side/back inspection render from `tools/mesh_to_cubes/bbrender.py`.
