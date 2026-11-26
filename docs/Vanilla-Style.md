# The Reason look: one art style for the whole pack

> 2026-11-26 (Andy): the pack's own resource pack, the **Reason look**, replaces the Excalibur option, aiming for
> **clean vanilla-plus**. It is the built-in pack `src/main/resources/resourcepacks/vanilla_style` (the id stays, so
> players' choice carries over; it shows as "Reason look"), on by default. Excalibur, its Fresh Animations patch and
> the graded `excalibur_style` copies are gone; installs that had them on get them switched off once
> (`PackResourcePacks.retireExcalibur`). The scan that found what still looks script-made, and Andy's picks of what to
> repaint, are in the project's `art-overhaul/pack-art-scan/` folder and the Pack Art Scan pick list.

Goal: everything in the pack looks like one hand drew it, in vanilla Minecraft's style. Our mods' own art is fixed at
the source (each `tools/gen_*.py`); other mods' textures are replaced in this pack with our own paintings, so their jars
never change. Workflow adapted from the game-assets-enhancer skill (audit, style guide, one anchor, reuse over
regenerate); its fal.ai image generation and parallax parts don't apply to 16px art, so the art is painted as pixel grids.

## Style rules (what the audit measures)
- **16x16**, one frame per icon. 32px icons come down to 16, except textures on models drawn at double size
  (armory claymores, glaives, lances; Tinkers' `large/` parts), whose pixels already match vanilla's size on screen.
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

## Blocks, mobs and worn armour (2026-11-26)
The item rules above, carried over to everything else.
- **No per-pixel noise.** Never colour each pixel with a random jitter, a blur or a smooth gradient across a shape:
  that is what makes art look script-made. Texture is placed by hand (or by a fixed pattern): vanilla stone is
  clusters of 2 to 4 pixels in 4 or 5 shades, not speckle.
- **Ramps, not ranges.** Each material gets one ramp of 4 to 7 shades (`tools/vanilla_style/vstyle.py`, `MAT`), and
  every pixel is one of its steps. A block face stays under about 40 colours, a 64x64 mob sheet under about 60.
- **Light from above.** On a mob, each part's top face is the light end of its ramp, the sides the middle, the
  underside one or two steps darker. Faces read at a glance: eyes 1 or 2 pixels, a highlight only where vanilla has one.
- **Patterns are shapes.** Stripes, spots, markings, trim and heraldry are clean blocks of colour with their own outline
  shade, never noise or gradients.
- **Variants are palette swaps.** A finish (blued, russeted, gilded), a dye colour or a coat is the same pixels in
  another ramp, plus at most a small decal (rivets, gilt edge, etched lines). Dyed variants can be one greyscale base
  tinted by the game, as vanilla leather is.
- **Worn armour** starts from vanilla's equipment UVs and shading (`armour.py`); metals use their ramp's mid tone to
  glint, as vanilla's diamond and gold do.
- **Shapes before colour.** An icon has to say what it is by its outline: a helm, a cuirass and a greave must not be
  the same box in three colours.
- Glows, auras, beams, particles and screens are meant to look different and are not held to these rules.

## The art check (`tools/art_audit.py`)
`tools/pr_checks.py` runs it on every item and block texture a PR adds or changes, and fails the PR when one has far
more colours than vanilla ever uses, soft gradients, or half-transparent pixels. Mob skins are only reported. Every
redraw in this pack passes; the script-made textures it was measured on don't. `python3 tools/art_audit.py --all`
counts what's left. A deliberate exception goes in `tools/pr_checks_allow.txt` with a reason.

## Audit (2026-10-16, `tools/vanilla_style/audit.py`)
1,870 modded item textures measured against vanilla 26.3's 830 (5th-95th percentile of each measure).

| Source | Icons | Verdict |
|---|---|---|
| Jaymo's Kitchen | 98 | Flat fills, black outlines: **redraw by hand** |
| Jaymo's Kingdoms | 48 | Flat stripes on armour and gear: **redraw by hand** |
| Jaymo's Powers (root) | 42 | Mostly close; 15 are 32px on normal-size models: **redrawn at 16px** |
| Powers armory (Age of Weapons) | 116 | 33 are 32px on models scaled ×2 in hand, which is vanilla's pixel density: **keep 32px** |
| Powers tinkering (Tinkers' parts) | 1,001 | Layered part sprites, already pixel-vanilla: leave |
| Pantry for Blockheads | 87 | Thick dark-brown outlines on every food. Art is all rights reserved, so no edits of its pixels (`vanillaize.py` is for our own and MIT art). Fresh paintings were tried and dropped: **keeps its own art** (Andy's call) |
| Naturalist | 186 | Close to vanilla: leave |
| Balkon's WeaponMod | 91 | Already close; iron katana redrawn (battleaxe, warhammer, musket, flail drafts in `paint_batch.py`) |
| MCA, Shippy Ships, Nature's Compass, Cooking for Blockheads, Old Cannons, Tool Belt | ~150 | Close: leave |
| TaCZ | 21 | Art licence is no-derivatives: leave |

## In the pack
- **Jaymo's Kitchen: 97 of 98** (the quiver keeps its own art, Andy's call) (`kitchen.py`, `carts.py`). Soups use vanilla's stew bowl, pies vanilla's pumpkin pie,
  carrot cake vanilla's cake, meats vanilla's cooked meats; carts, wagons and plows are drawn once and painted in each
  wood's vanilla planks colours. Fried egg and hot chocolate are in `paint_batch.py`.
- **Jaymo's Kingdoms: all 48** (`kingdoms.py`). The four armour sets are vanilla's iron armour in each set's colours
  (ronin lacquer and red cord, gladiator bronze, paladin steel with a blue tabard, warlord leather and fur); saddles,
  barding, books and oats start from their vanilla icons; scrolls, documents, staves and tack are drawn.
- **Jaymo's Powers: the 15 big icons** (`powers.py`). The straight relics (Caladbolg, Durandal, Hrunting,
  Tidebreaker, Voidreaver, Gram) are vanilla's sword pixel for pixel, each part in its relic's colours; Echo
  Cleaver, Soul Saber, Laevateinn and Sunfang keep their shapes; the Frost Lance is vanilla's spear in ice; the
  Void Flintlock, Grand Shield and Void Crown are drawn fresh in their own palettes.
- WeaponMod: iron katana (`paint_batch.py`).
- **Jaymo's Powers armour (2026-11-11, Tiv's ask: "solid outlines, pixel based detail, clean images")**
  (`armour.py`). Eight sets, item icons and worn layers (`entity/equipment/humanoid` and `humanoid_leggings`).
  Metal sets (Zanite, Gravitite, Arkenium, Neptune, Burrukai plate, Bog iron) start from vanilla's iron armour,
  hide and cloth sets (Beast pelt, Ichorcloth) from vanilla's leather armour plus its overlay, repainted by
  brightness rank in the set's 7-step ramp. Worn layers use the ramp's mid tone to glint, as vanilla's diamond and
  gold do. Trims: gold on Neptune, Burrukai and Ichorcloth, pale on Arkenium, fur on Beast pelt.
  The primitive hide sets (Caveman, Trapper's, Barbarian and a dark make of each, 2026-11-24; Andy picked all three
  redrawn styles) are drawn in `hide_sets.py`, which `armour.py` calls: letter-grid icons shaded from the top-left, and
  worn layers painted onto vanilla leather's UVs with its shading (Caveman keeps its old scraps cut,
  `masks/caveman_*.png`). They are our own art, so the same images are the mod's base textures. The script asserts
  the faults judges found in the drafts: outline shades on every silhouette pixel, nothing near the slot's grey on
  it, no checker dither, bone, fur and sinew apart in value. Ichorcloth's hood/robe icons
  and Bog iron's icons were already in this style and stay. One-off gear with placeholder art is redrawn in the
  same script: Traceur Boots and Gloves, Training Weights (both), Weighted Gi, and the Sealed Helm's worn layer.
  Scouter, Rig, Air Skin and Void Crown were already clean and stay.

Armour: `python -I tools/vanilla_style/armour.py <vanilla 26.3 assets/minecraft/textures folder>` (it needs the
`item` and `entity/equipment` folders).

Regenerate from the repository root with the vanilla 26.3 `textures/item` folder:
`python -I tools/vanilla_style/kitchen.py <folder>`, `kingdoms.py <folder>`, `powers.py <folder>` and `python -I tools/vanilla_style/paint_batch.py <folder>`.

## Powers art and other mods (2026-11-26, patches 6 and 7)
Powers: `tools/gen_fishing_species.py` draws the 32 fish icons (`tools/vanilla_style/fish_icons.py`, one hand-drawn grid per
species) and the 35 spawn eggs (`spawn_egg.py`, vanilla's egg). `tools/vanilla_style/powers_reason.py` runs after the old
generators: the dust pile, and `reason_clean`'s smoother plus a fold into few shades for the parts, blocks and skins the
art check reported (`entity/smoke/puff.png` is an effect and stays).
Other mods: `python -I tools/vanilla_style/other_mods.py <extracted jars> <vanilla textures>` writes replacements into the pack.

### Credits and licence basis
| Mod | What the pack replaces | Basis |
|---|---|---|
| Cube Animals | 162 entity skins and 58 items, cleaned from its own pixels | CC0 |
| Vanilla Constructs | 18 diamond buckets, tools and items, cleaned | MIT |
| Eternal Nether | 8 withered blocks and the netherite bell, cleaned | MIT |
| Shippy Ships | 19 sail pattern icons, fresh paintings (no pixel copied) | custom limited licence |
| Cooking for Blockheads | 16 dye colours of oven front, plain and lit (32 blocks), fresh paintings | All Rights Reserved |
| Stellarity | 7 icons (decayed clover, ender koi, three harvester swords, end city map, Deviant's Light disc), fresh paintings | own limited licence |
| Knight Armor and Weapons | 4 armour icons: vanilla's iron armour in steel, visor, strap and cloth added | All Rights Reserved |

Left alone on purpose: Simply Tents (every icon is a GeckoLib model sheet, a flat icon would not show), the knight
weapons and blade/handle parts (custom 3D models or odd sizes), Stellarity's 32 px `_held` and 3D weapon textures (their
models are drawn at double size), and Stellarity's animated prismatic pearl and punch arrow strips.

### Packs under and beside the Reason look (2026-11-26)
After the script-painted art fell short, Andy picked openly licensed packs instead (pick list
https://claude.ai/artifact/DJgp8W4XAaLJPyuTMVxBED). The game fetches each one from Modrinth on first launch
(`client/PackResourcePacks`); none is copied into our jars.

| Pack | Author | Licence | Where it sits |
|---|---|---|---|
| Pixel Perfection Fidelity R2.0 | SourAlien, from XSSheep's Pixel Perfection | CC BY 4.0 (its source CC BY-SA 4.0) | the base, under the Reason look |
| Medieval Items! 1.2 | its Modrinth author (project L6chz93w) | CC BY 4.0; changed: its axe textures are removed on download, at Andy's request | just above Pixel Perfection Fidelity |
| Framed Glass Panes V97 | Kai1907 | MIT | on top |
| Red Bricks 1.0.5 | Lobster Project | MIT | on top |
| Medieval Tactical Shield 1.1 | its Modrinth author (project pwDKTTxF) | MIT | on top |

Don't copy pixels from Pixel Perfection Fidelity into our own textures: share-alike would then cover them. New art for our
own items goes to an art agent, starting from the CC0 and MIT sources Andy picked (OpenGameArt weapon, RPG item, block
and ship sets; Computercraft Greentech, CC Industrialized, Militech, Wares Recalibration, Vanilla Military, Starlight 16x).
