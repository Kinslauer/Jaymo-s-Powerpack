# Zephyrus asset credit

Zephyrus, the west wind dragon, is our own model: nothing in it comes from another pack or from Saint's Dragons (whose
art is All Rights Reserved).

- Geometry: the old `ZephyrusDragonModel.createBodyLayer()`, box for box, rebuilt as `zephyrus.bbmodel` by
  `kingdoms/tools/gen_zephyrus_geo.py`.
- Hides and glowing markings: `kingdoms/tools/gen_zephyrus.py` (unchanged; same 256x256 UV layout).
- Animations: idle, walk, fly, hover, glide, land, sit, crash, swim (the old `setupAnim`'s poses, sampled from a port of
  it) and a new bite and tail sweep. Head look, climb pitch and the breath's jaw stay per-frame in
  `ZephyrusDragonRenderer`.
- Renders: `zephyrus-render.png`; `zephyrus-comparisons.png` puts each old pose over the new animation.
- Licence: `zephyrus-LICENSE.txt`.
