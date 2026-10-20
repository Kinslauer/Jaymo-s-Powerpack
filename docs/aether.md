# The Aether

The sky world: pale islands of holystone floating in an endless bright sky, mint grass and skyroot trees on top,
clouds you can walk into. Andy asked for it to be HEAVILY based on The Aether mod, so the names, shapes, numbers and
feel follow it; every line of Java and JSON is ours and every texture is painted by `tools/gen_aether.py` (clean room:
behaviour only, nothing copied). The dimension is `jaymospowers:aether`. The way in is Wind's (`power/wind/aether/WindAether`),
there is no portal. All numbers are `static final` constants at the top of the class named in brackets.

This page is the blocks and items (Track A1) and then the dimension itself (Track A2: terrain, biomes, features, sky). Mobs are
tracks C/D (see the design notes).

## How it is made

- Java: `com.jaymospowers.aether` (`Aether.init()` from `JaymosPowers`), client: `client/aether` (the rising block's
  renderer and quicksoil's speed cap). Ids are listed in `AetherBlocks` and `AetherItems`.
- `python -I tools/gen_aether.py` (Pillow only) writes the textures (`textures/block|item/aether/`), blockstates, models,
  item definitions, loot tables, recipes (`data/.../recipe/aether/`), tags and `lang/parts/aether.json`. It merges into
  the existing `data/minecraft/tags/...` files; rerunning changes nothing.
- No render-layer code: in 26.3 the layer comes from the texture (leaves and plants are cutout by their holes) and
  the aerclouds and quicksoil glass say `force_translucent` in their models.
- Creative tab: "The Aether" (grass block icon), all blocks and items below.

## Blocks

**Ground** [AetherBlocks, AetherGrassBlock]
- **Holystone** (pale silver-white; pickaxe, hardness 0.5), **Mossy Holystone** (same, mint moss), **Holystone Bricks**
  (2x2 holystone gives 4; hardness 2.0, blast 6.0). `#jaymospowers:aether_stone_ore_replaceables` holds holystone
  for the ores' worldgen.
- **Aether Dirt** (pale grey-cyan, shovel) is in `#minecraft:dirt`, so vanilla plants and saplings stand on it.
- **Aether Grass Block**: spreads onto aether dirt that has light 9+ above it (4 tries per random tick, like vanilla
  grass) and turns back to aether dirt when something solid covers it. Drops aether dirt, or itself with Silk Touch.
  In `#minecraft:grass_blocks` (and `animals_spawnable_on`, for the mobs).
- **Enchanted Aether Grass Block** (golden top): an ambrosium shard used on aether grass makes it. Never spreads, turns
  back to dirt when covered, drops aether dirt (itself with Silk Touch).
- **Quicksoil** (warm golden sand, shovel, hardness 0.5): friction 1.1, so you keep your speed on it; once sideways
  speed passes 1 block/tick the friction is held at 0.99 [QuicksoilSpeed, run on the player's own client].
  **Quicksoil Glass**: smelt quicksoil; amber, translucent, light 11, drops only with Silk Touch.
- **Icestone** (pale ice blue; stone pickaxe): freezes still water to ice and lava to obsidian (flowing lava to
  cobblestone) within radius sqrt(8) = about 2.83, when placed and every 20 ticks after [IcestoneBlock].

**Ores and metals** [AetherOreBlock, FloatingBlock, FloatingBlockEntity]
- **Ambrosium Ore** (hardness 3.0, any pickaxe): 1 ambrosium shard (+Fortune), 0-2 XP. **Zanite Ore** (stone pickaxe):
  1 zanite gemstone (+Fortune), 3-5 XP. Silk Touch drops the ore and no XP. **Block of Ambrosium** / **Block of Zanite**
  (5.0 / 6.0; zanite needs a stone pickaxe), both craft from 9 items and back.
- **Gravitite Ore** (iron pickaxe, drops itself): floats. Whenever there is free space above it (air, fluids, plants)
  it lifts off as a rising block (gravity 0.04, falling sand upside down) and settles back against the first ceiling
  (or after 30 s, or at the world's top).
- **Enchanted Gravitite** (iron pickaxe, hardness 5.0; smelt gravitite ore): floats only while powered by redstone,
  and comes down as an item rather than a block.

**Aerclouds** [AercloudBlock, BlueAercloudBlock] (translucent, wool sound, hardness 0.3; drop themselves)
- **Cold** (white) and **Golden**: only a hair-thin plate is solid, so you sink into the cloud and stand in it. Inside,
  your fall is forgotten and cut to 0.5% a tick, and there is never fall damage on any aercloud. Falling faster than
  a 2.5-block fall they become a 0.9-tall box and you land on top. Stacked clouds are one solid block.
- **Blue** (sky blue): nothing to stand on; anything sinking into it is flung up at 2 blocks/tick with a splash and a
  boing, unless sneaking (then it is a cold cloud).

**Skyroot** (grey-green bark, pale planks, mint leaves): **Skyroot Log / Wood / Stripped Log / Stripped Wood /
Planks / Leaves / Sapling**. An axe strips logs and wood (`Aether.init`). Planks come from any Aether log
(`#jaymospowers:aether_logs`, golden oak too), wood from 2x2 logs. Leaves drop their sapling with the vanilla chances
(5% to 10% with Fortune), sticks 2%+, and themselves with shears or Silk Touch. The sapling grows the configured
feature `jaymospowers:skyroot_tree` (made by the dimension's worldgen, see "The dimension" below).

**Golden Oak** (amber bark, gold leaves): **Golden Oak Log / Wood / Leaves / Sapling** (feature
`jaymospowers:golden_oak_tree`). Mined with an axe the log and wood also drop 1 to 2 golden amber. The leaves
drift gold specks down [GOLDEN_LEAF_PARTICLES] and now and then (0.005% to 0.025%, by Fortune) drop a golden apple.

**Crystal trees**: **Crystal Leaves** (pale cyan, cyan specks, sticks only) and **Crystal Fruit Leaves** (white apples
on them; breaking one drops a white apple).

**Plants**: **Berry Bush** (blue berries; stands on any soil, harmless): breaking it gives 1 to 3 blue berries
(+Fortune) and leaves a **Berry Bush Stem**, which grows the bush back on a random tick (1 in 60) with light 9+.
**White Flower** and **Purple Flower** (small flowers; suspicious stew gives Slow Falling 4 s and Nausea 12 s: the
stew recipes carry the effects). **Ambrosium Torch** (a torch that smokes instead of flames, light 14; a shard over a
stick makes 4).

## Items [AetherItems]

- **Ambrosium Shard**: right-click on aether grass makes it enchanted; right-click elsewhere heals one heart (not at
  full health), 1 s cooldown, uses up one [AmbrosiumShardItem].
- **Zanite Gemstone** and **Golden Amber**: materials (the tools and armour they are for are not in yet).
- **Blue Berry**: food 2 (saturation 0.4). **White Apple**: food 4, always edible, uncommon, and clears every harmful
  effect when eaten [WhiteAppleItem].

## The dimension (Track A2)

`jaymospowers:aether` is datapack plus four Java features. Wind is still the way in ([docs/wind.md](wind.md)). All tuning
numbers are in the JSON named below or are `static final` constants at the top of the feature class.

**Terrain** (`worldgen/noise_settings/aether.json`, `worldgen/density_function/aether/{islands,base_3d_noise}.json`)
- Islands only between y 14 and 128 of a 256-high world (min_y 0); holystone is the default block, sea level is -64 (no
  ocean), no aquifers, ore veins or carvers. Surface (`worldgen/material_rule/aether.json`): aether grass on the top
  layer, aether dirt under it for the surface depth, holystone below.
- The density is -0.15 + A x (-0.1 + B x (0.12 + n)): `n` is vanilla's old blended noise (the End's recipe: factors
  80/160, smear 8, scale 0.5 across and 0.7 up, so islands are tens of blocks wide and flat-ish), A ramps 0 to 1 over
  y 14 to 46 (thin undersides) and B ramps 1 to 0 over y 64 to 128 (thin tops, nothing above 128). A column is solid
  where the result is above 0. In practice: about 14% of the volume is solid, most of the mass between y 32 and 95,
  and the highest peaks near y 115.
- `tools/preview_aether_terrain.py [z] [seed] [out.png]` draws a 256x128 side view from the shipped JSON (numpy and
  Pillow; the noise is a stand-in for the game's, so shapes and densities match, not a given seed) and prints the share of
  the mass per 16 blocks of height. Re-tune the constants in the JSON and run it again.
- Climate: temperature and vegetation are the vanilla climate noises at scale 3 (regions a few hundred blocks wide);
  temperature is stretched 1.4x so the hot band (woodland) is reachable.

**Biomes** (`worldgen/biome/skyroot_{meadow,forest,grove,woodland}.json`; replace the old Aether II highlands)
All four share sky #C0C0FF, fog #9393BC, water #3F76E4, water fog #050533, grass and foliage #B1FFCB, no precipitation,
and the same feature list; only the tree count differs. `natural_mob_spawns` is empty everywhere (mobs are added later by
Fabric `BiomeModifications`). The multi_noise split follows The Aether's: meadow the coldest band and the dry middle,
forest the wet cold and temperate bands, grove the dry warm band, woodland the hottest.

**Features** (all in the biomes' own feature lists, never `BiomeModifications`)
| Step | Feature | Notes |
|---|---|---|
| raw generation | `quicksoil_shelf` (Java) | 1 in 5 chunks; 8 discs of radius 3.46 on island undersides below y 48 |
| lakes | `aether_water_lake` (vanilla `lake`) | 1 in 15 chunks; only fits where an island is thick enough |
| ores | `aether_ore_dirt` 20x33, `_icestone` 10x32, `_ambrosium` 20x16, `_zanite` 14x5 (y 0-75, half discarded in air), `_gravitite` 7x4 (trapezoid 0-74) and `_gravitite_buried` 5x3 | all in holystone |
| springs | `aether_water_spring` | 30 tries, y 8-128, on holystone or aether dirt |
| vegetation | `aether_trees_<biome>`, grass, tall grass, white and purple flowers, berry bushes | see below |
| top layer | `crystal_island` (Java, 1 in 20 chunks, y 50-110), `aercloud_cold` (1 in 7, y 32-96, 16 steps), `aercloud_blue` (1 in 24, 8 steps), `aercloud_golden` (1 in 75, y 96-127, 4 steps), `aercloud_bank` (1 in 3, y 2-30, 28 steps, big and thick) | clouds only take air |

- **Trees**: `skyroot_tree` is a vanilla `tree` (straight 4-6 trunk, blob crown radius 2 height 3, aether dirt under the
  trunk). `aether_trees` picks it, or 1 time in 100 the golden oak (`GoldenOakFeature`: 10-log trunk, branches of 1-2
  logs at heights 5-8, ragged round crown of radius 4). Placement per biome: attempts start at a random height (y 36-100)
  and scan down to the first island top, so every layer of islands gets trees, not just the highest. The attempt counts
  allow for misses: meadow 2, grove 5 (10% 7), forest 14 (10% 16), woodland 11 (10% 13), about 1, 2, 6, 5 trees a chunk
  where there is ground. The crystal island's tree (`CrystalIslandFeature`): 7 skyroot logs, four side logs at heights 2
  and 5, a diamond crown of crystal leaves (one in five fruit).
- Aerclouds are random-walk blobs (`AercloudFeature`: cloud kind, steps, blob radius, thickness in the JSON).
- Ground cover below the topmost layer is sparse: grass and flowers use the surface heightmap with a vertical spread of
  a few blocks.

**Sky and time** (`dimension_type/aether.json`, `timeline/aether_noon.json`, `tags/timeline/in_aether.json`)
- Eternal day: the Aether's timeline holds the sun at noon (angle 0), no moon or stars, no sunrise band; the overworld
  clock still ticks (the Overworld's own day is unaffected). Beds can set spawn but cannot be slept in (`can_sleep`
  never, with the message `message.jaymospowers.aether.no_sleep`); respawn anchors do not work.
- Clouds are low (height 9.5, seen from above, under the islands), white; no rain anywhere (no biome has precipitation).
- Skylight on, no ceiling, ambient light 0. Monsters spawn at light 0-7 (none do yet).

**Arriving and leaving**: see `docs/wind.md`. Arrival stands the player on bare ground (not on a tree) on the nearest
island; the fall home is below y 4 (the lowest islands and cloud banks sit above that).

**To check first in game**: that the dimension loads (the data uses 26.3 formats that were checked against the vanilla
data files, not run): terrain shape and density, the four biomes appearing, trees on islands, golden oak, a crystal
island, clouds above and below, quicksoil under island edges, a lake, a fresh Wind arrival.

## What is not in yet

- Aether tools and armour (skyroot, holystone, zanite, gravitite) and Skyroot's double drops, the Holystone tool's
  ambrosium chance, zanite's wear-speed.
- Dungeons, bosses, the Valkyrie, the glowstone portal (Wind is the way in, by Andy's choice), Aerogel, unstable
  obsidian, frosted ice, holiday leaves, quicksoil glass panes.
- Fire: Aether wood and leaves are tagged to burn from lava (`logs_that_burn`) but have no spread/burn odds yet; no
  fuel or compost values; no skyroot stairs, slabs, fences, doors, signs, boats or buckets.
- Aether grass tint, bonemeal on aether grass (flowers and grass), the water-to-holystone rule over quicksoil.
- Mobs (Moa, Phyg, Flying Cow, Sheepuff, Aerbunny, Zephyr, Cockatrice, Aechor Plant, Swets, Whirlwind): tracks C and D.

## To check first in game

Place a gravitite ore under open sky (it should rise and stop under the first ceiling); jump into a blue aercloud
(and sneak in one); fall onto a cold one from 20 blocks; put icestone beside a water source and a lava pool; use a
shard on aether grass; mine zanite ore with a wooden pickaxe (no drop, no XP) and a stone one; bone-meal a skyroot
sapling and a golden oak sapling (the trees are `jaymospowers:skyroot_tree` and `golden_oak_tree`).
