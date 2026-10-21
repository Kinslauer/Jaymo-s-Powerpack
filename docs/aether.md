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

## Aether II blocks: stone, soil, ice, ores, clouds, building

The Aether is being rebuilt as The Aether II's Holy Isles (`DESIGN2.md`). This track (N1) added its stone, soil, ice,
ore, cloud and building blocks to `AetherBlocks`/`AetherItems`; `python -I tools/gen_aether.py` paints and writes all
of their assets and data, and `python -I tools/check_aether_assets.py .` checks that every block has a blockstate,
models, an item definition, a loot table, a name and textures (it also parses every JSON under `src/main/resources`).
They all sit in the "The Aether" creative tab. **Holystone is now 1.5/6** (it was 0.5), Aether II's number; icestone
keeps 0.5. Aether grass now converts a neighbour on 1 random try in 10 [AetherGrassBlock.SPREAD_ODDS], as Aether II's
does.

**Stones** [AetherBlocks.initStones]
- **Undershale** (dark slate, 3.0/6), **Agiosite** (purple, 1.5/6), **Ichorite** (brown, 4.0/6), **Ferrosite** (rusty
  grey, 1.5/6).
- **Irradiated Holystone** (sickly green veins): standing on it gives Poison I for 2 s, once a second
  [IrradiatedHolystoneBlock].
- **Crude Scatterglass** (cyan crystal rock, 1.5/6, half-transparent): drops 2-4 scatterglass shards (+Fortune), or
  itself with Silk Touch.
- **Holystone Rock** [HolystoneRockBlock]: a pebble pile, no collision, instant break, pops off when its ground goes.
- **Pointed Holystone** [PointedHolystoneBlock]: a stepped cone standing on the ground; landing on it hurts like a
  stalagmite (the fall counts double, FALL_MULTIPLIER).
- Bricks: **Undershale**, **Agiosite**, **Icestone**, **Ichorite** and **Faded Holystone Bricks** (4 from 2x2 of the
  stone; faded ones from 4 holystone bricks and a bone meal). Decorative: **Holystone Flagstones**, **Faded Holystone
  Flagstones**, **Holystone Pillar** (a rotated pillar), **Holystone Keystone**, **Holystone Button** and **Holystone
  Pressure Plate** (stone-type, so they sound and behave like vanilla stone ones).
- Stairs, slabs and walls for holystone, mossy holystone, holystone bricks, faded holystone bricks, undershale,
  undershale bricks, agiosite, agiosite bricks, icestone bricks, ichorite and ichorite bricks: crafted the vanilla way
  and cut on a stonecutter (from the block and, for bricks, from the stone they come from).

**Soils** [AetherBlocks.initSoils, AetherSoilTools, AetherPathBlock]
- **Coarse Aether Dirt**, **Aether Dirt Path** and **Aether Farmland**. A shovel on aether grass, aether dirt or coarse
  aether dirt makes the path; a hoe makes farmland (coarse dirt turns to plain aether dirt first), with air needed
  above. Farmland is a vanilla `FarmlandBlock` reverting to aether dirt, and is in `supports_crops`/`grows_crops`, so
  vanilla crops grow on it. The path and farmland drop aether dirt.
- **Shimmering Silt** (blue-grey) and **Ferrosite Sand** (rust) fall like sand; **Ferrosite Mud** is a copy of mud.

**Ice and snow** [AetherBlocks.initIce]
- **Arctic Snow Block** and **Arctic Snow** (1-8 layers, drops that many).
- **Arctic Ice** (friction 0.98, melts near light like ice) and **Arctic Packed Ice** (0.98, no melting); both drop
  only with Silk Touch.
- **Fragile Arctic Ice** [FragileArcticIceBlock]: a player standing on it cracks it 5 ticks later (BREAK_DELAY), and it
  also breaks on 1 random tick in 5; it leaves nothing.
- **Arctic Ice Crystal** [ArcticIceCrystalBlock]: hangs from a ceiling, no collision, light 3.

**Ores and metals** [AetherBlocks.initOres]
- In holystone (3.0/3.0): **Holystone Quartz Ore** (quartz, 2-5 XP), **Arkenium Ore** (inert arkenium, no XP),
  **Glint Ore** (glint gemstone, 3-5 XP). In undershale (4.5/3.0): **Undershale Ambrosium / Zanite / Arkenium / Glint
  Ore**, and **Undershale Gravitite Ore**, which floats like the holystone one.
- **Corrobonite Ore** [CorroboniteOreBlock] (undershale-based, iron tier): every random tick it grows a **Corrobonite
  Cluster** (an amethyst-cluster-like block, light 4, drops one crystal) onto one free face.
- Storage: **Block of Arkenium / Glint / Corrobonite / Golden Amber** (9 of the item) and **Block of Gravitite** (4
  enchanted gravitite).
- Items: **Inert Arkenium**, **Glint Gemstone**, **Glint Coin** (1 gemstone makes 4 coins and back),
  **Corrobonite Crystal**, **Scatterglass Shard**.

**Glass** [AetherBlocks.initGlassAndClouds]
- **Scatterglass** (smelt crude scatterglass, or 4 shards): clear cyan glass. **Scatterglass Pane** and **Quicksoil
  Glass Pane** (16 from 6 of the block), both Silk Touch only.

**Aerclouds** (all the cold cloud's rules unless said; no fall damage in any of them)
- **Green Aercloud** [GreenAercloudBlock]: entering shoves you 1.0 block a tick in a random horizontal direction, at
  most once every 20 ticks per entity (COOLDOWN).
- **Purple Aercloud** [PurpleAercloudBlock]: has a facing (the way you were looking when you placed it, shown by the
  arrow on its front face) and flings whatever is inside 2.0 blocks a tick that way; sneaking settles instead.
- **Storm Aercloud** [StormAercloudBlock]: dark grey, crackles; inside it you take 1 lightning damage every 40 ticks.

**Building** [AetherBlocks.initBuilding]
- **Cloudwool** (2 from 2x2 cold aercloud) and **Cloudwool Carpet**, in white and the 16 dyes (dye one piece of
  cloudwool); they are in the vanilla `wool` and `wool_carpets` tags.
- **Arkenium Lantern** (a lantern around an ambrosium torch, light 15), **Arkenium Chain**, **Arkenium Bars** (iron
  bars' shapes, from inert arkenium), all in the vanilla `lanterns`/`chains`/`bars` tags.

Tags: everything is in `mineable/pickaxe` or `mineable/shovel`, with `needs_stone_tool` (arkenium, glint, zanite ores)
and `needs_iron_tool` (corrobonite, undershale gravitite); the stairs, slabs, walls, buttons, plates, sands, ices,
snow, mud and wools join their vanilla tags, so vanilla tools, walls, pathing and glass panes work on them.
`#jaymospowers:aether_undershale_ore_replaceables` holds undershale, for the worldgen (track W).

**To check first in game**: that the creative tab holds them all with real textures and names; a hoe and a shovel on
aether grass; crops on aether farmland; standing on irradiated holystone and on fragile arctic ice; walking into a
green, purple and storm aercloud; a corrobonite ore left to grow clusters; the stonecutter list for holystone.

## Items [AetherItems]

- **Ambrosium Shard**: right-click on aether grass makes it enchanted; right-click elsewhere heals one heart (not at
  full health), 1 s cooldown, uses up one [AmbrosiumShardItem].
- **Zanite Gemstone** and **Golden Amber**: materials (the tools and armour they are for are not in yet).
- **Blue Berry**: food 2 (saturation 0.4). **White Apple**: food 4, always edible, uncommon, and clears every harmful
  effect when eaten [WhiteAppleItem].

## The dimension (Aether II, Track W)

`jaymospowers:aether` is Aether II's Holy Isles, remade from behaviour notes: datapack plus Java features. Wind is still the
way in ([docs/wind.md](wind.md)). Everything below is written by `tools/gen_aether_world.py` (terrain, dimension, biomes,
tags) and `tools/aether_world_features.py` (every configured and placed feature and each biome's lists); edit those and
rerun `python3 -I tools/gen_aether_world.py`, never the JSON. Tuning numbers sit at the top of the terrain section of the
generator and in `static final` constants at the top of each feature class. `python3 -I tools/check_aether_world.py` checks the
data (every id exists, one feature order, saplings have trees).

**Shape** (`worldgen/noise_settings/aether.json`, `worldgen/density_function/aether/**`, `worldgen/noise/aether/*`)
- The dimension runs y -32 to 384 (`dimension_type/aether.json`: min_y -32, height and logical height 416); the terrain
  is generated in y 0 to 384 (noise settings `min_y` 0, height 384; cells 4 across, 4 up so tunnels resolve). Sea level -64:
  no ocean, no aquifers, no ore veins; water comes only from lakes and springs. Holystone is the default block.
- `final_islands` = squeeze(interpolated(max(min(base islands, caves, Hestveil), upper islets gated above y 128))) + beardifier.
  - **Lowland islands** (`terrain/base_islands`): a big mask noise (octave about 640 blocks) decides where an island is; each is a
    lens, its top at y 108 plus hills (tops y 95-135), its thickness growing with the mask (up to about 105 blocks, bottom never
    below y 6), a 3D roughness on both faces. About 55% of the columns have lowland ground.
  - **Caves** (`terrain/caves`): two noises near zero make tunnels (radius 0.16 in noise units), faded out between y 88 and 108
    so the surface stays whole; `terrain/hestveil` opens big caverns in y 8-58.
  - **Upper islets** (`terrain/shattered_islands`): two more island layers (tops near y 168 and 226, up to 70 and 60 blocks
    thick, at least 6) that only exist above y 128, so most of the sky between stays open.
- Climate (`climate/*`): temperature, humidity, erosion and weirdness are shifted noises; depth is a curve of height (cave layer,
  lowland, middle, high) that sorts biomes by layer.
- Surface (`worldgen/material_rule/aether.json`), in order: arctic snow patches in Frigid Sierra, arctic packed ice patches in the
  arctic biomes, irradiated holystone patches in Battleground Wastes, ferrosite in Magnetic Scar and under the soil of the
  magnetic biomes, mossy holystone patches, aether grass over aether dirt above y 80 (caves below stay bare), undershale between y 56
  and 78 and below.
- `tools/preview_aether_terrain.py [--seed --z --x0 --span --scale --map-span --map-step --no-map --out]` evaluates these JSON files
  (numpy and Pillow; a stand-in for the game's noise, so shapes match, not a given seed) and writes a side view (`aether_slice.png`),
  a lowland height map with the upper islets shaded, and a biome map, and prints the mass per 32 blocks of height.

**Biomes** (`worldgen/biome/*.json`, 16, names in `lang/parts/aether_world.json`; tags `#jaymospowers:is_aether` (all),
`is_aether_highfields`, `is_aether_magnetic`, `is_aether_arctic`, `is_aether_irradiated` in `tags/worldgen/biome`)
- Highfields (temperature -0.4 to 0.65): Shimmering Basin (lake variant, weirdness at least 0.3), Flourishing Field (dry), Verdant
  Woods (mid), Shrouded Forest (wet). Magnetic (erosion at least 0.485): Magnetic Scar (weirdness at least 0.3), Violet Highwoods
  (dry), Turquoise Forest (mid), Glistening Swamp (wet). Arctic (below -0.4): Frozen Lakes (lake variant), Sheer Tundra (dry), Enduring
  Woodland (wet), Frigid Sierra (the middle and high layers, depth 0.3 up). Irradiated (above 0.65): Battleground Wastes (dry),
  Contaminated Jungle (wet). Hestveil Caverns takes the cave layer (depth below -1). Expanse is registered and never placed.
- Temperature, downfall and the sky, fog and water colours are from the notes; precipitation on in all; `natural_mob_spawns` empty
  (Fabric `BiomeModifications` adds the animals into the highfields, magnetic and arctic tags, see "Aether II animals"; the moa only
  through its nests);
  creature spawn probability 0.2; the irradiated biomes drop end-rod motes.
- Every biome's feature lists are cut from one master order (`MASTER`/`VEGETAL` in the features file) so the game never meets two
  orders. Anything placed by height rather than by surface (ores, clouds, springs, cloudbed, quicksoil shelves, crystal islands, cave
  pointed holystone) is in every biome, Hestveil too, because the biome at a height is whichever the depth router picks there.

**Placement**: a feature scan can be at most 32 blocks long, so surface features (trees, plants, lakes, boulders, spikes) start at a
random height over the lowland tops (y 110-138, `LOW_START`) and scan down to the first ground; about half the attempts land, so the
counts are the notes' per-chunk counts as attempts. The upper islets get `HIGH_SHARE` (half) of the attempts with starts in y 150-276.

**Features** (`worldgen/feature`, `worldgen/placed_feature`; Java ones in `com.jaymospowers.aether.world` unless noted)
- **Trees**: one feature per sapling, `<tree>_tree` (skyplane, skybirch, skypine, wisproot, wisptop, greatroot, greatoak, greatboa,
  amberoot) and `skyroot_tree`, plus the forms the biomes use (large, short, nest, ferns, leaf piles, snowy, irradiated), vanilla
  `tree` features; a vanilla `random_selector` per biome (`trees_<biome>`) with the notes' weights; fallen logs are vanilla
  `fallen_tree`. `golden_oak_tree` (Java, `GoldenOakFeature`) is rare in the verdant and shrouded woods.
- **Ores** (all of N1's): agiosite, ambrosium, arkenium, corrobonite, glint, gravitite, zanite, holystone quartz, icestone, crude
  scatterglass, with the notes' sizes and counts, in holystone and undershale (`aether_stone_ore_replaceables`,
  `aether_undershale_ore_replaceables`), plus ichorite pockets in the undershale.
- **Water**: vanilla `lake` ponds (plain, arctic with packed ice rims, swamp with ferrosite mud), springs, arilum on the water, arctic
  ice sheets over cold lakes (an `arctic_ice` disk, since there is no freeze step).
- **Plants and ground**: grass, ferns, bushes, flower fields per zone, blueberries, orange trees, valkyrie sprouts, brettl, holystone
  rocks and twigs, magnetic shrooms and huge magnetic shrooms (vanilla `huge_red_mushroom` with our blocks), rotshrooms (plants only),
  bryalinn, shayelinn and ambrelinn moss patches (`vegetation_patch`), coarse dirt, silt, ferrosite sand, quicksoil and packed ice
  discs, boulders (`block_blob`).
- **Java**: `cloudbed` (a sea of cold aercloud at y -30..-18 from value noise of the world seed; scenery, below the fall-home line),
  `ice_spike` (foot radius 4.25, about 7.5 blocks of height per block of radius, one in ten mega at 6.25), `ferrosite_spire`
  (`kind` spike or pillar), `moa_nest` (woven skyroot sticks ring on level ground), `pointed_holystone` (one spike on the nearest floor,
  in the open or in caves), `aercloud` (cold, blue, golden, green, purple with random facing, storm; placed in the surface, lower,
  middle and high bands from the notes), `quicksoil_shelf` (now with `min_y` 40, `max_y` 110), `crystal_island` (a rare sight in the
  sky, y 150-260), `golden_oak`.
- All Java writes go through `AetherFeatures.put`/`isAir`, which test `canWrite` (quiet, unlike `ensureCanWrite`), so wandering
  features near a chunk border just stop.

**Sky and time** (`dimension_type/aether.json`)
- The Overworld's day and night (`timelines` is `#minecraft:in_overworld`, clock `minecraft:overworld`); beds work only when it is
  dark (`when_dark`); respawn anchors do not work. Clouds are white at height 320.33. Skylight on, no ceiling, ambient light 0 with
  colour #0a0a0a; monsters spawn at light 0-7 (no Aether monsters yet).

**Arriving and leaving**: see `docs/wind.md`. Arrival stands the player on bare lowland ground (not on a tree, not on an islet overhead);
the fall home is below y -8.

**Not done from the notes**: Hestveil and alkahest blocks, density-function lakes (we use vanilla `lake` features), the freeze step for
arctic water, sunken swamp trees, the tree decorators beyond leaf piles, ferns and snow, rotshroom shelves and huge rotshrooms, the
structures (other tracks), custom sky, weather and cloud renderers.

**To check first in game**: that the dimension loads with a fresh Aether (the server boot test did, and every biome was found with
`locate biome`); the look from a Wind arrival (big islands, islets above, open sky); trees and ores; caves; lakes; the cloudbed from
the underside; falling out below y -8 and landing home; day and night; a moa on a highfields island.

## What is not in yet

- Aether tools and armour (skyroot, holystone, zanite, gravitite) and Skyroot's double drops, the Holystone tool's
  ambrosium chance, zanite's wear-speed.
- Dungeons, bosses, the Valkyrie, the glowstone portal (Wind is the way in, by Andy's choice), Aerogel, unstable
  obsidian, frosted ice, holiday leaves, quicksoil glass panes.
- Fire: Aether wood and leaves are tagged to burn from lava (`logs_that_burn`) but have no spread/burn odds yet; no
  fuel or compost values; no skyroot stairs, slabs, fences, doors, signs, boats or buckets.
- Aether grass tint, bonemeal on aether grass (flowers and grass), the water-to-holystone rule over quicksoil.
- Whirlwind and the other mobs of Aether I that Aether II does not have.

## To check first in game

Place a gravitite ore under open sky (it should rise and stop under the first ceiling); jump into a blue aercloud
(and sneak in one); fall onto a cold one from 20 blocks; put icestone beside a water source and a lava pool; use a
shard on aether grass; mine zanite ore with a wooden pickaxe (no drop, no XP) and a stone one; bone-meal a skyroot
sapling and a golden oak sapling (the trees are `jaymospowers:skyroot_tree` and `golden_oak_tree`).

## Passive animals (track C) [aether.mob]

The Aether's five passive animals, ours from behaviour notes (`mobs.md`): own classes in `com.jaymospowers.aether.mob`,
models and renderers in `client/aether/mob`, skins painted by `tools/gen_aether_mobs.py` (it reads each model's
`part(...)` lines for the texture layout, so rerun it after editing a model; run `gen_aether.py` first when running both:
that one rewrites `lang/parts/aether.json` and this one adds its block back at the end). Spawn eggs and the three moa
eggs sit in the "The Aether" creative tab. All spawn through Fabric `BiomeModifications.addSpawn` into
`#jaymospowers:is_aether` minus the Hestveil caverns (the moa only into `#jaymospowers:is_aether_highfields`; the biome files are not
touched), as the creature category, on the `animals_spawnable_on` tag (aether grass is in it), in daylight. The biomes'
own `creature_spawn_probability` (the Aether uses 0.25) decides how many are there when a chunk is first made.
**Aether II changed the numbers (see "Aether II animals" below, which wins): spawns are now by the three climate tags
(`AetherAnimals`), sizes and healths are the notes', and the moa has no natural spawn but its nests.**

| Animal | Spawn weight, group | HP | Notes |
|---|---|---|---|
| Phyg | 1, 2-4 | 10 | breeds on oranges and blue berries; valkyrie wings or a saddle to ride |
| Sheepuff | 1, 2-4 | 8 | breeds on oranges and blue berries; all 16 colours |
| Flying cow | 1, 2-4 | 10 | breeds on oranges and blue berries; milk with a bucket; valkyrie wings or a saddle |
| Aerbunny | 14, 1-3 | 6 | tamed with an orange; no breeding drops |
| Moa | none (nests only) | 35 | blue 100 / white 50 / black 25 by weight; territorial |

- **Riding** [RideableAnimal]: right-click a saddled one (not sneaking) to mount; steer with the movement keys, jump
  with the jump key. A ride is simulated on the rider's own client, like any mount, so it needs no packet; the jump key
  is read through `AetherMobs.jumpHeld`, which the client sets. Mounts shrug off any fall (`SAFE_FALL_DISTANCE` plus
  `causeFallDamage`), and a saddle comes off whole when one dies.
  - **Phyg and flying cow** [WingedAnimal]: a high jump from the ground (0.62), one flap more in mid-air (0.55), and
    holding jump while falling glides (falls at most 0.1 a tick; 0.5 otherwise). The cow carries a rider at 0.75 speed.
    The wings are folded on the ground and spread and beat in the air.
  - **Moa** [Moa]: jumps in mid-air 3 (blue), 4 (white), 8 (black), one per press with a 10-tick cooldown; they come back
    on the ground. Falls at most 0.1 a tick loose or gliding, 0.5 ridden. (Aether II's stamina, growth and nest rules
    replace the old ones: see "Aether II animals".)
- **Sheepuff** [Sheepuff]: grazes the aether grass under it (it turns to aether dirt); after 2 patches it puffs up: falls
  at most 0.05 a tick, no fall damage, jump strength 0.9 (a normal sheep's is 0.42). Shearing a puffed one lets the air
  out and gives 2-4 wool (it keeps its wool); a plain shearing gives 1-3 and leaves it bare until it grazes again. Colour
  on spawn: see "Aether II animals". Drops a kirrid loin and its wool unless bare.
- **Aerbunny** [Aerbunny, AerbunnyRide]: right-click with an empty hand to carry it on your head (sneak to put it down).
  Carried: your fall is capped at 0.25 a tick (no fall damage for a drop that gentle, within the "descents under 0.5 b/t"
  rule), and once you have fallen more than 1.1 blocks jump hops you up (0.55) and puffs the bunny; at most 2 hops per time in the air (refilled on the ground), so it
  can't chain into flight. This runs on your client (movement is yours), so other players see the bunny but
  the hop is yours. Hit by a player it flees for 100-149 ticks (and can't be picked up while it does).
- Drops (`data/jaymospowers/loot_table/entities`, Aether II's): phyg 1-3 raw taegore meat; flying cow 1-3 burrukai rib cut;
  sheepuff 1-2 kirrid loin (+ wool from the entity); aerbunny and moa nothing. Meats come cooked from a burning mob; Looting.
- Tuning is in `static final` constants at the top of each class (`AetherAnimals` has the spawn weights).

To check first in game: spawn each from its egg; saddle a phyg (jump, flap, glide), a flying cow and a hatched moa;
let a sheepuff graze twice and shear it puffed and plain; pick up an aerbunny and jump off a cliff.

## Aether II woods and plants (Track N2)

Aether II's trees' blocks and its plants, in `AetherFlora` (registry; `Aether.init` calls it, and it adds its items to the
"The Aether" tab itself) with the small block classes `FloraPlantBlock`, `OrangeTreeBlock`, `WovenSticksBlock`.
`python -I tools/gen_aether_flora.py` (Pillow only; borrows the painting helpers of `gen_aether.py`, so run that one first)
writes every texture, model, blockstate, item definition, loot table, recipe (`data/.../recipe/aether/flora/`), tag and the
lang part `aether_flora.json`; `python -I tools/check_aether_flora.py` checks that every registered id has all of them.
Clean room: behaviour from the notes, every line and pixel ours. The shaped blocks reuse vanilla's block set type and wood type
(oak's sounds and press times).

- **Wood sets**: greatroot (dark brown), wisproot (pale lilac-grey), amberoot (golden brown with amber flecks) each have log,
  wood, stripped log and wood, planks, stairs, slab, fence, fence gate, door, trapdoor, button and pressure plate (the usual
  vanilla recipes). Skyroot gets the eight shaped blocks (its log family and planks are Aether I's). **Mossy Wisproot Log**
  (wisproot log and a bryalinn moss block) and **Amberoot Deposit** (a log with amber chunks; an axe also drops 1 to 2 golden
  amber). An axe strips all of them (`AetherFlora`'s own callback, like skyroot's in `Aether`). Each new wood has a log tag
  `#jaymospowers:<wood>_logs` (planks come from it); `#jaymospowers:aether_flora_logs` joins them for `#minecraft:logs`.
  `#jaymospowers:aether_logs` is left as it was, because skyroot planks are made from it and a greatroot log must not give them.
- **Leaves** (cutout, no tint, like Aether I's): skyplane (pale green), skybirch (yellow-green), skypine (dark teal needles),
  wisproot (pale violet), wisptop (violet), greatroot (deep green), greatoak (green), greatboa (frosted blue-green), amberoot
  (gold), irradiated (olive; yellow specks drift down [IRRADIATED_LEAF_PARTICLES]; no sapling). Each drops its sapling with
  the vanilla chances, sticks, and itself with shears or Silk Touch.
- **Saplings** (skyplane, skybirch, skypine, wisproot, wisptop, greatroot, greatoak, greatboa, amberoot): `AetherSaplingBlock`
  growing `jaymospowers:<tree>_tree` (Track W makes those features; until then a sapling just doesn't grow). `AetherFlora.TREES`
  lists the keys. All saplings and the small flowers can be potted (`AetherFlora.POTTED`).
- **Ground plants**: Aether Short, Medium and Long Grass and Bush Fern are tinted with the biome's grass colour (client:
  `AetherFloraClient`; the textures are grey), replaceable, drop seeds or themselves with shears. **Aether Bush** (hardness
  0.65, squat leafy block), **Holystone Twig** (flat, no collision, stands on any solid top).
- **Flowers**: Hesperose, Poasprout, Lilichime, Pluracian (small flowers with suspicious stew recipes: regeneration 8 s,
  saturation, night vision 10 s, jump boost 6 s), **Tarabloom** (two blocks tall), **Blade Poa**, **Satival Shoot** (drops a
  satival bulb, food 2), **Valkyrie Sprout** (1 to 2 valkyrie wings), **Brettl Plant** (1 to 2 brettl grass; 9 make a
  **Brettl Grass Bundle**, which turns back into 9).
- **Orange Tree** [OrangeTreeBlock]: two blocks tall, ages 0 to 3 on random ticks in light 9+ (1 in 5). Ripe, right-click or
  breaking gives 1 to 3 oranges (food 4; Fortune adds); picking sets it back to age 1. **Wyndberry** is only an item for now.
- **Arilum** (lily-pad-like, placed on water) and **Blooming Arilum** (light 5; drops 1 to 2 arilum bulbs, itself with Silk Touch).
- **Mosses**: bryalinn (green), shayelinn (cyan), ambrelinn (yellow): block (hardness 0.1; plants stand on it) and carpet, plus
  vines for the first two (vanilla vine rules, climbable).
- **Mushrooms**: Magnetic Shroom (light 6; the huge ones are Magnetic Shroom Block and Stem), Rotshroom and Rotshroom Block
  (huge-mushroom blocks; no huge growing yet, the worldgen places them).
- **Misc**: Woven Skyroot Sticks (8 pixels tall, a moa nest stands on it) and Skyroot Leaf Pile (a carpet).

Not done, for later: signs, bookshelves, beds and the decorative planks (shingles, beams) of the building notes; fire and
compost values (no precedent in the repo); a leaf pile for every leaf type; mossy wisproot wood; huge-mushroom and orange-tree
worldgen (Track W). The old item name "Blue Berry" still has to become "Blueberry" in `gen_aether.py` (its lang part owns the key).

## Aether II monsters (Track H) [aether.monster]
Eleven hostile mobs, their shots, four status effects and five drops. Code is in `aether/monster/` (registry
`AetherMonsters`, effects `AetherEffects`, shots `AetherShot`, base class `AetherMonster` with slash/impact/pierce
resistance) and `client/aether/monster/` (one `AetherMonsterRenderer` with a `Look` per mob, ten generated models; both
swets share `SwetModel`). Tuning numbers are `static final` constants at the top of each class. Spawns use
`BiomeModifications` against the `#jaymospowers:is_aether*` tags (by `TagKey` only).

| Mob | Behaviour | Drops | Spawn weight |
|---|---|---|---|
| Zephyr | flies; 100-tick Blow Away cycle (circle, breath, gust box), webbing shot every 60 ticks at 8-40 blocks | cloudtwine | all Aether biomes (1-2 in irradiated) |
| Tempest | hovers relative to its target (the Aether II hover bug is fixed); 65-tick thunderball volley; hides in sun | cloudtwine | the 12 ordinary biomes + irradiated |
| Cockatrice | venomous bite with a lunge; 4-dart volley; flees sun, digs in | cockatrice feather | 4 |
| Blue Swet / Golden Swet | hops; latches (max 3 per player, slows, drains hunger); water shrinks it; size 0.6-1.25 | swet gel, swet sugar | 2 / 1 (groups of 1-2) |
| Aechor Plant | rooted; ring of 10 toxic darts every 60 ticks; only hoe, axe or shears hurt it | aechor petal | 5 |
| Carrion Sprout | rooted; night trap holds and bites 2 every 30 ticks | none | 3 |
| Skephid | wall and ceiling climber; hangs upside down, webbing shot; flees sun | none | 1 |
| Arkenium Taluton | 8 damage, knockback 1.2, 40-tick swing | none | 3 |
| Gravitite Taluton | slow fall, wall climb, debris every 100 ticks | none | 4 |
| Bladeshroom Hunter | buries and unburies; 38-tick cut, lunge | none | 2 in the magnetic biomes (our addition) |

Each mob has a loot table (`loot_table/entities/<mob>.json`, drops use `killed_by_player` and the enchanted-bonus chance), a
spawn egg and a skin (plus glow overlays for seven). The five new drop items have textures, item definitions and lang.

- **Effects**: Webbed (-85% speed, no jump), Venom (1 magic damage every 30 ticks, can kill), Toxin (every 12 ticks, stops at
  1 HP), Electric Shock (-3 attack damage, lightning damage every 25 ticks). Icons are in `textures/mob_effect/`.
- **Shots** (`AetherShot.Kind`, one entity type and item each, drawn with `ThrownItemRenderer`): zephyr web, skephid web
  (both stun a blocking shield), thunderball (non-destructive blast plus a shock field), venomous dart, toxic dart, debris
  (accelerates). Monsters never hit each other directly (a thunderball's blast and field still reach them).
- **Files**: `tools/gen_aether_monsters.py` reads the model `part()` lines and paints skins, glows, sprites, eggs, effect
  icons, item JSON, loot tables and `lang/parts/aether_monsters.json`; `tools/check_aether_monsters.py` validates them.

Simplifications and additions: no buildup meters (plain effects instead); no cutting items (Aechor and Carrion cuttings left
out); hoe, axe or shears stand in for trowels; Skephid ceiling AI is approximate (leap, then cling); Bladeshroom Hunter gets
a spawn because hostile.md gives it none.

To check first in game: Zephyr and Tempest flying and aiming (flying speed, hover point); swets latching and detaching;
Skephid hanging upside down (renderer flip); Aechor Plant warning when hit with the wrong tool; Bladeshroom Hunter burying;
sun behaviour of Tempest, Cockatrice and Skephid; shield stun from webbing balls.

## Aether II animals (Track P) [aether.mob]

Behaviour and numbers from `aether2-study/passive.md`, our own code, models and skins. `AetherAnimals` registers the new
animals, their items, the moa egg block, the placements and every spawn (all the animals, old and new); `Zone` is the
highfields / magnetic / arctic climate read from the biome tags (`#jaymospowers:is_aether_<zone>`). Art and data come from
`tools/gen_aether_mobs.py` (older animals, sheepuff colours, their loot) then `tools/gen_aether_animals.py` (the rest; both read
the models' `part(...)` lines); `tools/check_aether_animals.py .` checks the pieces hang together. Names are in
`lang/parts/aether_animals.json`.

**Spawns** (weight, group; the biome files are not touched): highfields, magnetic and arctic biomes get taegore 14 (1-2),
burrukai 14 (1), kirrid 14 (1-2), aerbunny 14 (1-3), phyg, flying cow and sheepuff 1 (2-4 each), glitterwing and shroudwing
1 (1-2, ambient), aerwhale 1 (1). Prismallards (1, 1-3) float on the highfields' lakes (not the dry Flourishing Field). The
irradiated biomes and the caves have no animals. Ground animals need light above 8 (aether grass etc.); the aerwhale needs open
sky and passes one natural roll in 40. No natural spawn: moa (nests), skyroot lizard (leaves).

| Animal | Size | HP | Notes |
|---|---|---|---|
| Phyg | 0.95 x 0.85 | 10 | valkyrie wings in hand = calm: climb on without a saddle (ride speed 0.6; the saddle works too); put the wings away and it bolts |
| Flying cow | 0.95 x 1.1 | 10 | same with ride speed 0.7; milk with a bucket |
| Sheepuff | 0.9 x 1.25 | 8 | puffs on its 2nd graze; 16 colours; wild colour by climate |
| Aerbunny | 0.55 x 0.45 | 6 | an orange tames it, 1 in 3 per try (then it sits; brettl grass heals 2); wild ones can still be carried |
| Moa | 1.25 x 2.35 | 35 | see below |
| Kirrid | 0.95 x 1.35 | 16 | hops; wool shell; plate |
| Burrukai | 1.5 x 1.95 | 26 | neutral; attack 3, knockback 4, armour 6, stun on ram |
| Taegore | 1.3 x 1.7 | 18 | shy digger |
| Skyroot lizard | 0.95 x 0.35 | 4 | from leaves |
| Prismallard | 0.5 x 0.6 | 4 | lays eggs |
| Glitterwing / Shroudwing | 0.75 / 0.5 | 2 | ambient insects |
| Aerwhale | 3 x 3 | 20 | drifts through the sky |

- **Food**: orange or blueberry tempts and breeds phyg, flying cow, sheepuff, kirrid, burrukai and taegore (`isAnimalFood`).
- **Sheepuff colours** (wild, by climate; every other colour comes from dye): light gray 5%, gray 5%, cyan 5%, then 3% yellow
  (highfields) / light blue (magnetic) / brown (arctic), 1 in 500 magenta in the highfields, else white. 16 skins
  `sheepuff_<dye>`.
- **Kirrid** [Kirrid]: moves in hops (0.5-0.65 jump power in the notes; here `HOP_UP`/`HOP_FORWARD`, a wait of 10 ticks on
  the ground). Two plated adults ram each other (charge 80 ticks, within 2 blocks each hit has a 1% chance to knock the plate off,
  which drops a kirrid plate and regrows after 6000 ticks; 600-2400 ticks between rams). Grazing aether grass (every 600-1200
  ticks, 60 ticks) turns it to aether dirt and brings the wool back sooner. Shears: 1-3 cloudwool of its colour, regrows in 2400 ticks;
  dye recolours the wool. Wild colour by zone: highfields undyed cloudwool (white, brown, black 5% each, lime 1/500), magnetic
  light blue (gray, lime, green 5%; purple 1/500), arctic white (brown, magenta, pink 5%; blue 1/500). The arctic kind has horns.
  Skins `kirrid_<zone>_<wool>` (51). Drops 1-2 kirrid loin and its wool.
- **Burrukai** [Burrukai]: neutral. Hurt by a living thing it stays angry 600 ticks (babies never fight back); it winds up 30 ticks,
  then rams at speed 2.25, and a hit within its width does damage and stuns (shield-style) for 70 ticks; if it misses it gives up
  after 50 ticks. Drops 1-3 rib cut, 0-2 beast pelt, 1-3 burrukai plate. Skins `burrukai_<zone>`.
- **Taegore** [Taegore]: flees sprinting players within 24 blocks (1.7x) unless they wear a piece of the item tag
  `#jaymospowers:beast_pelt_armor` (empty now; the gear track fills it with beast pelt armour). Every 9600 ticks (the first after 2400) it picks a diggable spot 10-40 blocks
  away (aether grass or dirt, coarse dirt, shimmering silt, ferrosite mud, moss), walks there and digs for 240 ticks: one of
  skyroot stick, skyroot pinecone, valkyrie wings, blueberry, satival bulb, holystone twig, holystone rock. It remembers 20
  places. Drops 1-3 raw taegore meat and 1-3 beast pelt. Skins `taegore_<zone>`.
- **Skyroot lizard** [SkyrootLizard]: not in the biomes. When a leaf block's loot is dropped (broken by a player, or decay), 1 in 10
  makes one at that spot, of the kind matching the leaves (10 kinds, skins `skyroot_lizard_<leaf>`). It flees sprinting players
  within 8 blocks. A stick (plain or skyroot) used on it turns it into a skyroot lizard on a stick (food 4).
- **Prismallard** [Prismallard]: floats on lakes; panics at 1.4; tempted and bred with arilum bulbs; an adult lays a prismallard egg
  every 6000-11999 ticks (it drops as an item; thrown, it breaks and 1 egg in 8 hatches a chick); falls at 0.6 speed. Drops 1-2
  leg, 1-2 feather.
- **Glitterwing** (11 kinds) and **Shroudwing** (6) [Insect]: hover about, keep 6 blocks from players who are not sneaking, land and
  rest every 200-600 ticks, no fall damage, cannot be leashed. The kind comes from the biome (wet, lush, highfields, dark).
- **Aerwhale** [Aerwhale]: drifts toward points 16-32 blocks away through open air, never into blocks; a long low song every 1200
  ticks. No riding, no drops.
- **Moa** [Moa, MoaEggBlock]: no natural spawn except the worldgen nests (`MoaNestFeature`: a woven skyroot sticks ring and bed
  with a moa egg block in 75% of nests and a grown wild moa guarding 35%). A wild grown moa is territorial: a player within 5
  blocks (Manhattan) of its nest and within 18 of it is attacked; hurt by a player, it and the grown wild moas within 16 stay angry
  for 600 ticks. Babies never fight back; a moa a player raised never fights its owner. A moa egg block on woven skyroot sticks
  hatches in three stages of 4000 (+0-299) ticks, about 12000 in all; elsewhere 8000 a stage. An egg a player placed hatches a
  chick they raised (saddle and ride, shears, saddle bags later); a nest egg a wild one. The egg item used on the ground still
  hatches a raised chick at once. **Chick growth**: hungry for 8000 ticks once every 2400-4799; feeding in that window (aechor petal or
  blueberry) counts 2 if more than 4000 ticks were left, else 1, and moves it a stage on; a missed window also moves it a stage
  on; three stages make an adult. How well it was fed shifts its stamina at that moment: nothing fed -4, exactly 3 -1, else +1
  (the notes' code never gives the -2 case; we do not either). **Stamina** (jumps in mid-air): 3 blue, 4 white, 8 black, shifted by
  feeding, never under 3; each air jump costs 1 and starts a 300-tick wait that counts down 12 a tick on the ground, 1 in the
  air. Adults heal 5 from food, 1 HP in 900 ticks. Only a grown moa a player raised takes a saddle and gives 4 moa feathers to
  shears (up to 2000 ticks between; 32 durability).
- **Items**: raw/cooked taegore meat (3/8 food), burrukai rib cut and cooked (3/8), kirrid loin and cutlet (2/6), prismallard leg
  and roast (2/6), skyroot lizard on a stick (4), beast pelt, burrukai plate, kirrid plate, prismallard feather, moa feather,
  prismallard egg, and a spawn egg each. Smelting, smoking and campfire recipes for the four meats are in `data/.../recipe/aether`.
- **Moa egg block** (`moa_egg`): properties kind (blue/white/black), stage (0-2, cracks), owned, low (an egg on the 8-pixel sticks is
  drawn and shaped 8 pixels lower). Breaking it drops the egg item of its kind.

Not in: Edward the trader and the cloud skiff (other tracks), saddle bags, the beast pelt armour, bestiary pages, the shroudwing
and glitterwing biome gates beyond a name check (see `Insect.pickVariant`), the blightfly's glow pass.

To check first in game: spawn each from its egg; ride a phyg with valkyrie wings held and put them away; tame an aerbunny with
oranges; walk into a moa nest (and out); place an egg on sticks and wait; shear a puffed sheepuff; watch two kirrids ram; break
leaves for a lizard; throw prismallard eggs; follow a taegore to a dig.

## Sentry Ruins and the Slider (Track D, phase 3) [aether.dungeon]

Package `com.jaymospowers.aether.dungeon` (client: `client/aether/dungeon`), lang `lang/parts/aether_dungeon.json`, art and data from `tools/gen_aether_dungeon.py`, sanity check `python3 -I -B tools/check_aether_dungeon.py .`.

- **Structure** `jaymospowers:sentry_ruins`: underground, built in code (`SentryRuins`, `RuinLayout` picks the pieces, `RuinPiece` builds them). Biome tag `has_structure/sentry_ruins` is the Aether biomes minus hestveil and expanse; spacing about 12/6 chunks. Layout: the boss room, a tunnel to a chest room, 0-4 more rooms (lounge, deposit, cold, testing, workshop, forge), a lobby, flights of stairs and a small surface ruin. A layout that would break out into the air, hit itself or reach more than 112 blocks is thrown away (up to 24 tries). "Ground" is the top of the highest run of rock at least 16 thick, because thin floating bits sit above the islands.
- **Blocks**: sentry_bricks family (stairs, slab, wall, button, lightstone, flagstones, tile, pillar; LIT and POWERED show the sentry current), sentry_spawner, sentry_trap, sentry_crate (30% mimic), locked variants (undershale, undershale bricks, sentry bricks, sentry base bricks, flagstones, lightstone), boss_doorway, treasure_doorway. Locked blocks can only be broken with the Slider dead (they unlock on death in the boss room and the vault behind it).
- **Mobs**: Slider (350 HP, pickaxe only, bound to its room, crushes and launches, springs a trap every 1/16 of its health, boss bar, opens the doors on death), Detonation Sentry, Sentry Golem (1-2 in some rooms), Mimic. Tuning numbers are `static final` at the top of each class and of `RuinLayout`/`RuinPiece`.
- **Loot**: `loot_table/chests/sentry_ruins/{common,rare,deposit,cold,boss}.json`, existing items plus vanilla gear.
- **Checked**: compile check clean (only the 12 expected old errors); the dev server boots and `/place structure jaymospowers:sentry_ruins` in the Aether builds it with one Slider and no log errors.
- **Not checked in game**: how the rooms look and play, the fight itself, the client renders and skins, how often the structure is found while exploring.

## Aether II structures (Track S, phase 4) [aether.structure]
Everything is built in code (no NBT), like the Sentry Ruins: one structure type `jaymospowers:aether_structure` (codec: `plan`, optional `variant`) and one piece type `jaymospowers:aether_piece`. Each piece builds per chunk in a local (u, v, y) frame (`Site`), every random choice is a pure function of position and seed, and writes are clipped to the chunk. Ground comes from `Ground` (top of the highest rock run at least 16 thick; `flat()` and `buried()` checks). Generator: `tools/gen_aether_structures.py` (assets, loot, tags, structure and structure_set JSON, lang). Checker: `tools/check_aether_structures.py` (0 problems). Run both with `python3 -I -B`.

- **Structures** (17 `structure/*.json`, 9 `structure_set`s, 17 `has_structure` biome tags): outpost (2 variants, 2x2 outpost campfire, 22 wide), camps (highfields, magnetic, arctic, 31), watchtower (3 heights), animal den with animal stash, Veradexian ruins (temperate, arctic), ancient henge, Veradexian library (temperate, arctic, 25x36), Veradexian aqueduct (9x49), Brexallen ruins (buried 14-28 under ground), undercloud mineshaft (hub, tunnels, junctions, storage rooms), irradiated remnants (bunker, settlement), infected guardian tree (33x80, spiral stair, floors every 12, vault at the top).
- **Spacing**: surface ruins 14/8, surface structures 18/8, mineshafts 12/6, underground ruins 4/2, dens 8/5, aqueducts 10/5, irradiated 8/3, outposts 28/18, guardian trees 22/10. Several sets keep clear of outposts, irradiated of sentry ruins.
- **Blocks**: holystone, veradexian and brexallen vases (shatter on break, projectile or explosion and drop their loot; silk touch keeps them), animal stash, outpost campfire (2x2, right-click lights it and records it), ambrosium campfire, prayer candle, guardian log/wood/stripped/infected, guardian planks/stairs/slab/roots/lucent roots/lamp, faded holystone pillar and keystone. Axes strip guardian wood.
- **Outpost respawn**: lighting an outpost campfire adds it to the player's `outpost_campfires` attachment (copyOnDeath). On respawn with no bed, anchor or forced spawn, the player wakes at the nearest remaining lit campfire (`OutpostTracker`).
- **Loot**: `loot_table/chests/...` and `vases/...`. Track G gear ids are only in separate listable pools behind the `TRACK_G` flag in the generator (False, vanilla stand-ins: iron/stone pickaxe, elytra for gliders). Other stand-ins to swap later: fossilized zanite, shifting glass, resonant stone, pendants, guidebook page, bolts and darts (arrow), skyroot bucket, rope (string), bedroll (carpet), swet jelly, irradiated dust, holystone bookshelf, trowel, discs.
- **Merge notes**: track G may also register `ambrosium_campfire` (a duplicate id crashes at registration). Shared tag files (logs, planks, stairs, slabs, wooden_*, mineable, needs_stone_tool, aether_logs, stripped_logs) are edited here and may conflict.
- **Checked**: compile check clean (12 expected errors). The dev server boots; `/place structure` built the watchtower, animal den, guardian tree, temperate ruins, arctic and magnetic camps, aqueduct, irradiated bunker and settlement, and natural generation produced Brexallen ruins and mineshafts, with no structure errors in the log. Outpost, highfields camp, henge, library and arctic ruins did not place at the test spots (biome or flatness filter); not verified.
- **Not done**: ambrosium campfire cooking, slanted and ladder mineshaft corridors, guardian donation box and pews, a guardian tree boss, the vase pot-breaking-tool nuance. How the builds look in game is unchecked.

## Aether II gear

Track G of the Aether II rebuild (`DESIGN2.md`): tools, hammers, armour, gliders and the altar, in
`com.jaymospowers.aether.gear` (client: `client/aether/gear`). `python -I tools/gen_aether_gear.py` paints every
sprite and armour layer (our own shapes, vanilla's flat shade-ramps) and writes the item definitions, models, equipment
assets, recipes, tags and `lang/parts/aether_gear.json`; `python -I tools/check_aether_gear.py .` checks it all hangs together
(ids the Java registers against assets, recipes, tags and lang). All numbers are `static final` constants at the top of
`GearTier`, `ArmorSet`, `ArmorAbilities`, `GravititeTools`, `AercloudGliderItem`, `AltarRecipes` and `AltarBlockEntity`.
Everything is in the "The Aether" creative tab. Behaviour and numbers are from the Aether II notes (`structures.md` section 5).

**Tools** [GearTier, AetherGear]. Five metals, each with a **Shortsword**, **Hammer**, **Pickaxe**, **Axe**, **Shovel** and
**Hoe** (the hoe stands in for Aether II's trowel): `<metal>_shortsword`, `_hammer`, `_pickaxe`, `_axe`, `_shovel`, `_hoe`.
| Metal | Durability | Speed | Attack bonus | Enchant | Mines like | Made from |
|---|---|---|---|---|---|---|
| Skyroot | 59 | 2 | 0 | 15 | wood | skyroot planks |
| Holystone | 131 | 4 | 1 | 5 | stone | holystone |
| Zanite | 250 | 6 | 2 | 14 | iron | zanite gemstone |
| Arkenium | 1561 | 6 | 2 | 14 | iron | arkenium plate |
| Gravitite | 1561 | 8 | 3 | 10 | diamond | enchanted gravitite |
The shortsword is quick (-2.2, a slash: it is in `#minecraft:swords`, so mobs with a weak spot to slashes take more); the
hammer is the heavy one (3 added damage, 3/4/5/5/6 by metal as in the notes, -3.3 speed) and is *not* a sword, so its blows count as impacts. Pickaxes, axes,
shovels and hoes join the vanilla tool tags (the Slider takes pickaxes only, so these work on it). **Zanite Shears**: 238
durability, vanilla shears' behaviour. **Arkenium Plate**: smelt or blast inert arkenium, or use the altar. Tools are mended with
their metal on an anvil or at the altar.
- **Gravitite tools** [GravititeToolItem, GravititeTools]: right-click a block to lift it as a rising block (the same
  `FloatingBlockEntity` gravitite ore uses), if it holds no items (no block entity), is not unbreakable, is not a
  two-high block or a bed, and has air or a plant above. The block's own use comes first (doors, levers), and sneaking with
  the axe, shovel or hoe does the ordinary job (strip, flatten, till). A lift costs 1 durability.

**Armour** [ArmorSet, ArmorAbilities]: Helmet, Chestplate, Leggings and Boots (`<set>_helmet` and so on) of six sets.
| Set | Durability x | Defence (helm/chest/legs/boots) | Enchant | Toughness | Made from |
|---|---|---|---|---|---|
| Beast Pelt | 5 | 1 / 3 / 2 / 1 | 15 | 0 | beast pelt |
| Burrukai Plate | 5 | 2 / 5 / 4 / 1 | 15 | 0 | burrukai plate |
| Zanite | 15 | 2 / 6 / 5 / 2 | 9 | 0 | zanite gemstone |
| Arkenium | 15 | 2 / 6 / 5 / 2 | 10 | 1 | arkenium plate |
| Gravitite | 33 | 3 / 8 / 6 / 3 | 10 | 2 | enchanted gravitite |
| Neptune | 33 | 2 / 6 / 5 / 2 | 10 | 1 | (boss loot only; mends with zanite) |
Full-set bonuses (every five ticks the set is looked at; the attributes are transient):
- **Zanite**: +0.15 attack speed, +2 mining speed, +5% walking speed.
- **Arkenium**: +0.3 explosion knockback resistance, and 16% of every hit that doesn't kill is healed straight back (the game
  has no hook to shrink a blow before it lands).
- **Gravitite**: falls hurt half as much (fall damage multiplier -0.5), and **one extra jump in mid-air** (1.25x a jump, a push of
  0.2 the way you steer): the client makes the move (movement is the player's own) and sends `DoubleJump`; the server checks
  the set and forgets the fall; it comes back on landing or in water.
- **Neptune**: water movement efficiency +2 (capped at 1 by the game), +1.5 oxygen.
- Beast Pelt: a taegore isn't scared of whoever wears any piece (`#jaymospowers:beast_pelt_armor`, read by the animals
  track). Burrukai Plate has no bonus. Gloves (zanite, arkenium, gravitite, neptune) are not made.

**Aercloud gliders** [AercloudGliderItem]: `cold_`, `golden_`, `blue_` and `purple_aercloud_glider`. Hold right-click in
mid-air to glide (not on the ground, in water, riding or flying): the fall slows to -0.025 a tick (sneak to dive at -0.15), you
steer by walking (forward full, sideways half, back a quarter, 0.03 a tick; golden 0.04), and you never take fall damage while
it is open. Opening one costs 1 durability (cold 5, golden 30, blue 3, purple 3); the glide lasts 160 ticks (golden 400), then
an 80-tick wait; letting go early waits 2 ticks. **Blue** throws you up 2 blocks a tick as it opens, **purple** multiplies your sideways
speed by 6 (at least 0.2, at most 3 a tick), each once until you land. The steering is on the gliding player's own client
(Armory's bomb glider is a vehicle, so no precedent; this is the rule quicksoil's speed cap follows: players move on their own
side); the server counts the time, wears the item and forgets the fall. Recipes: three aerclouds of the colour over cloudtwine, a centre piece (ambrosium shard, golden amber or
zanite) and sticks.

**Enchanted fruit**: **Enchanted Blueberry**, **Enchanted Orange**, **Enchanted Wyndberry**: 6 hunger, saturation 1.0 and
Regeneration II for 6 seconds. Made at the altar.

**Altar** [AltarBlock, AltarBlockEntity, AltarMenu, AltarRecipes; client AltarScreen]. Craft it from holystone and a zanite
gemstone (`HZH / .H. / HHH`). Right-click for its screen: the input in the middle, a ring of eight ambrosium-shard slots round
it (clockwise from the top; a recipe asks for the first N, ringed in gold), the output on the right, an arrow that fills as it
works. A recipe needs the first N shard slots each holding a shard and room for its result; it works 200 ticks (a missing shard
or full output drains the work by 2 a tick; changing the input starts over), then uses one shard from each of the N slots and
one input item. Shift-click puts shards into the slot with the fewest. What it does:
- **Repairs** any damaged Aether tool, hammer, shears, armour piece or glider, by tier (shards): skyroot and beast pelt 1,
  holystone 2, burrukai 3, zanite 4, arkenium 5, neptune 6, gravitite 8; gliders cold 1, golden 3, blue 2, purple 2.
- **Quicksoil to quicksoil glass** (1), **inert arkenium, arkenium ore or undershale arkenium ore to arkenium plate** (3, 4, 4),
  **fruit to enchanted fruit**: blueberry 1, orange 1, wyndberry 2.
- Dropping the altar drops what is on it.

**Loot** [tools/gen_aether_dungeon.py]. The Sentry Ruins' crates now use this gear in place of the vanilla stand-ins: the boss
crate gives two neptune pieces, a roll of zanite or neptune armour, zanite tools and shears, maybe a golden, blue or purple glider,
enchanted fruit and arkenium plates; the rare crate gives burrukai armour, zanite and holystone tools, cold and golden
gliders and beast pelts; common crates give holystone tools and hammers, burrukai plates and beast pelts. Not made (not
in this mod yet): Hammer of Demolition, Sentry Boots, kinetic thrusters, gloves, pendants, charms, shifting glass.

**Check first in game**: gliding (hold right-click in the air: slow fall, steering, the blue and purple boosts once, no fall
damage, landing refuels), the gravitite extra jump (a double tap of jump in the air in a full set), lifting blocks with a gravitite
tool, the altar's screen and a repair, and the zanite and arkenium set bonuses on the attribute readout.
