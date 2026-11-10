# Redstone golem asset credit

The model, its two textures (body and emissive) and its idle and walk animations are **"Redstone Golem (Minecraft
Dungeons)" by polyjo**, from Blockbench Workshop:
https://blockbenchworkshop.com/model/polyjo/redstone-golem-minecraft-dungeons (source copy:
`tools/bbmodels/redstone-golem-minecraft-dungeons.bbmodel`). The design is Mojang's Minecraft Dungeons mob.

- Licence: **CC BY 4.0**, https://creativecommons.org/licenses/by/4.0/ (`redstone_golem-LICENSE.txt`).
- Changes: converted with `tools/bbmodel_geckolib.py` for GeckoLib 5; its idle and walk renamed `idle` and `walk`;
  four animations of ours added by `kingdoms/tools/gen_redstone_golem_geo.py` (the script is the source, not hand
  edits): `pound`, `eruption`, `hit` and `death`. No cubes or pixels were changed. The emissive texture is drawn
  glowing as `textures/entity/redstone_golem_glow.png`. Drawn at 0.94 size with a 1.5-block shadow.
- Inspected renders: `redstone_golem-render.png`, `redstone_golem-animations.png` (idle, hit, death) and
  `redstone_golem-comparisons.png` (the old code-posed pound and eruption beside the new keyframes, tick for tick).
