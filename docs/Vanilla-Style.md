# Vanilla style for every modded item

Goal: every item in the pack looks like Mojang drew it. Delivered as the built-in resource pack
`src/main/resources/resourcepacks/vanilla_style` (on by default; switch it off under Resource Packs to compare).
Workflow adapted from the game-assets-enhancer skill (audit, style guide, one anchor, reuse over regenerate); its
fal.ai image generation and parallax parts don't apply to 16px item icons, so the art is painted as pixel grids.

## Style rules (what the audit measures)
- **16x16**, one frame per icon. 32px icons come down to 16.
- **No black outlines.** The outline is the darkest shade of the colour it wraps, one step lighter on the lit side.
- **Light from the top-left.** Lit outline and a glint just inside the upper-left edge; darkest outline and a shade
  band on the lower-right.
- **Material ramps are vanilla's own.** Iron, gold, diamond, netherite, copper, stone and wood each use the 7-step
  ramp read off vanilla's sword (`tools/vanilla_style/vstyle.py`, `MAT`); handles are vanilla's stick (4 browns).
  The same shape painted in another material matches vanilla's tool of that material.
- **Reuse vanilla's pixels** where vanilla has the thing: chestplates start from `iron_chestplate`, soups from
  `mushroom_stew`'s bowl, spears could point at vanilla's spears.
- **5 to 18 colours per icon**, no half-transparent pixels, item fills about half the square, tools on the diagonal
  (handle bottom-left, head top-right).

## Audit (2026-10-16, `tools/vanilla_style/audit.py`)
1,870 modded item textures measured against vanilla 26.3's 830 (5th-95th percentile of each measure).

| Source | Icons | Verdict |
|---|---|---|
| Jaymo's Kitchen | 98 | Flat fills, black outlines: **redraw by hand** |
| Jaymo's Kingdoms | 48 | Flat stripes on armour and gear: **redraw by hand** |
| Jaymo's Powers (root) | 42 | Mostly close; 15 are 32px: **redraw those** |
| Powers armory (Age of Weapons) | 116 | 33 are 32px: **bring to 16px** |
| Powers tinkering (Tinkers' parts) | 1,001 | Layered part sprites, already pixel-vanilla: leave |
| Pantry for Blockheads | 87 | Thick dark-brown outlines on every food. Art is all rights reserved, so no edits of its pixels (`vanillaize.py` is for our own and MIT art): **paint fresh** |
| Naturalist | 186 | Close to vanilla: leave |
| Balkon's WeaponMod | 91 | Already close; iron katana redrawn (battleaxe, warhammer, musket, flail drafts in `paint_batch.py`) |
| MCA, Shippy Ships, Nature's Compass, Cooking for Blockheads, Old Cannons, Tool Belt | ~150 | Close: leave |
| TaCZ | 21 | Art licence is no-derivatives: leave |

## In the pack
- **Jaymo's Kitchen: 97 of 98** (the quiver keeps its own art, Andy's call) (`kitchen.py`, `carts.py`). Soups use vanilla's stew bowl, pies vanilla's pumpkin pie,
  carrot cake vanilla's cake, meats vanilla's cooked meats; carts, wagons and plows are drawn once and painted in each
  wood's vanilla planks colours. Fried egg and hot chocolate are in `paint_batch.py`.
- Kingdoms: ronin and gladiator chestplates. WeaponMod: iron katana (`paint_batch.py`).

Regenerate from the repository root with the vanilla 26.3 `textures/item` folder:
`python -I tools/vanilla_style/kitchen.py <folder>` and `python -I tools/vanilla_style/paint_batch.py <folder>`.
