# Impaler asset credit

The Impaler geometry, default texture and animation data are ported from **Inhabitants**:
https://github.com/JeremySeq/Inhabitants

Upstream release: v1.2.1, commit `c3461b5aab57802e83b135010fb3a5e812479c1f`.

- Inhabitants lead coder: **JeremySeq**.
- Impaler artist, animator and designer: **Catalyst** (credited in the upstream README).
- Upstream team: **Team Synapse**; the upstream MIT notice names **Team Obsidian**.
- Licence: **MIT**. The unmodified upstream notice is preserved in `impaler-LICENSE.txt`.

Source files:

- `src/main/resources/assets/inhabitants/geo/impaler.geo.json`
- `src/main/resources/assets/inhabitants/animations/impaler.animation.json`
- `src/main/resources/assets/inhabitants/textures/entity/impaler/default.png`

Port changes for Jaymo's Kingdoms:

- Changed the geometry identifier from `geometry.unknown` to `geometry.impaler`, placed assets under `jaymoskingdoms`, and used GeckoLib 5's `geckolib/models/entity/` and `geckolib/animations/entity/` folders.
- Preserved all 19 source bones, 21 cubes, per-face UVs, rotations, hierarchy and default texture pixels. The thin alpha-mapped horn, spike and tail plates are intentional source geometry.
- Preserved all 7 source animations: `idle`, `walk`, `run`, `bite`, `hurt`, `scream` and `spike throw`, including Catmull-Rom keyframes, the spike-throw sound marker and scream instruction markers. No animations were added or removed.
- Added `impaler.bbmodel`, an editable GeckoLib Blockbench project with the texture embedded and every bone keyframe.
- Checked the fields against the pack's GeckoLib `kSxHvs99` jar (5.5.7): its 1.12.0 geometry and 1.8.0 animation parsers support the source fields, including per-face UVs, Catmull-Rom interpolation, `sound_effects` and `timeline`; no format downgrade was needed.
- `impaler-render.png` is the visual front/side/back inspection render from `tools/mesh_to_cubes/bbrender.py`.

Added for the Impaler's gear and coats (same upstream release, same MIT notice):

- The other three coats and every coat's glow mask: `textures/entity/impaler/{default,albino,dripstone,forlorn_hollows}{,_glowmask}.png`, now under `textures/entity/impaler/` (the old `textures/entity/impaler.png` was the default coat).
- Item art: `textures/item/impaler_spike.png`, `textures/item/concussion_arrow.png` (as `concussive_arrow.png`), and the spike drill's ten heat and running frames in `textures/item/spike_drill/`.
- `textures/entity/concussion_arrow.png` (as `concussive_arrow.png`) and `textures/mob_effect/concussion.png`.
- Sounds: the Impaler's attack, death, hurt, idle, scream, spikes and concussion takes (`sounds/entity/impaler/`, now `sounds/impaler/`), the concussion buzz (`sounds/effects/concussion_buzz.ogg`, now `sounds/effect/`), and the spike drill's start, dig and stop (`sounds/item/spike_drill/`).
- The concussive arrow and spike drill recipes follow upstream's shapes. The items' behaviour is written fresh for Kingdoms.
