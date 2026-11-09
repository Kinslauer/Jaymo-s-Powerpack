# Aetherion asset credit

Aetherion's body plan comes from the public-domain **CC0** GeckoLib dragon Andy supplied for the original Kingdoms feature. The existing project source did not record that reference's author or URL. No external model, texture or animation file is copied into this conversion.

- The rebuilt geometry, wings, head, hides, markings and all keyframes are the project's own work from `kingdoms/tools/gen_aetherion.py` and `AetherionDragonModel.java`.
- `kingdoms/tools/gen_aetherion_geo.py` now writes the editable `aetherion.bbmodel` and GeckoLib geometry/animation JSON with deterministic IDs.
- Animations: idle, walk, fly, glide, land, sit, crash, bite, Cataclysm and tail sweep. Head look, climb pitch and breath jaw remain per-frame renderer posing.
- Licence/reference note: `aetherion-LICENSE.txt`; also listed in `textures/entity/bb/CREDITS.txt`.
