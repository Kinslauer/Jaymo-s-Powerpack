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
  (Fabric `BiomeModifications` adds the animals: everything in `#is_aether` but Hestveil, the moa in the highfields only);
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
- Mobs (Moa, Phyg, Flying Cow, Sheepuff, Aerbunny, Zephyr, Cockatrice, Aechor Plant, Swets, Whirlwind): tracks C and D.

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

| Animal | Spawn weight, group | HP | Notes |
|---|---|---|---|
| Phyg | 10, 3-4 | 10 | breeds on blue berries; saddle and ride |
| Sheepuff | 12, 3-4 | 8 | breeds on wheat |
| Flying cow | 12, 2-5 | 10 | breeds on wheat; milk with a bucket; saddle and ride |
| Aerbunny | 11, 3 | 6 | no breeding |
| Moa | 8, 1-3 | 35 | blue 100 / white 50 / black 25 by weight; lays eggs |

- **Riding** [RideableAnimal]: right-click a saddled one (not sneaking) to mount; steer with the movement keys, jump
  with the jump key. A ride is simulated on the rider's own client, like any mount, so it needs no packet; the jump key
  is read through `AetherMobs.jumpHeld`, which the client sets. Mounts shrug off any fall (`SAFE_FALL_DISTANCE` plus
  `causeFallDamage`), and a saddle comes off whole when one dies.
  - **Phyg and flying cow** [WingedAnimal]: a high jump from the ground (0.62), one flap more in mid-air (0.55), and
    holding jump while falling glides (falls at most 0.1 a tick; 0.5 otherwise). The cow carries a rider at 0.75 speed.
    The wings are folded on the ground and spread and beat in the air.
  - **Moa** [Moa]: jumps in mid-air 3 (blue), 4 (white), 8 (black), one per press with a 10-tick cooldown; they come back
    on the ground. Falls at most 0.1 a tick loose or gliding, 0.5 ridden. A wild moa can't be saddled: use a moa egg
    (laid by a moa every 5 to 10 minutes while grown and unridden, or from the creative tab) on the ground to hatch a
    chick that you raised; feed it blue berries to hurry the 20 minutes it takes to grow up, then saddle it. It heals
    1 HP every 45 seconds. (Not in: Nature Staff sit/follow, tamed moas hunting swets, the Aether's incubator: the egg
    hatches the moment it is used.)
- **Sheepuff** [Sheepuff]: grazes the aether grass under it (it turns to aether dirt); after 2 patches it puffs up: falls
  at most 0.05 a tick, no fall damage, jump strength 0.9 (a normal sheep's is 0.42). Shearing a puffed one lets the air
  out and gives 2-4 wool (it keeps its wool); a plain shearing gives 1-3 and leaves it bare until it grazes again. Colour
  on spawn: 5% light blue, 5% cyan, 5% lime, 3% pink, 1 in 500 purple, else white. Drops mutton and its wool unless bare.
- **Aerbunny** [Aerbunny, AerbunnyRide]: right-click with an empty hand to carry it on your head (sneak to put it down).
  Carried: your fall is capped at 0.25 a tick (no fall damage for a drop that gentle, within the "descents under 0.5 b/t"
  rule), and once you have fallen more than 1.1 blocks jump hops you up (0.55) and puffs the bunny; at most 2 hops per time in the air (refilled on the ground), so it
  can't chain into flight. This runs on your client (movement is yours), so other players see the bunny but
  the hop is yours. Hit by a player it flees for 100-149 ticks (and can't be picked up while it does).
- Drops (`data/jaymospowers/loot_table/entities`): phyg porkchop 1-3 + feather 0-1; flying cow leather 0-2 + beef 1-3;
  sheepuff mutton 1-2 (+ wool from the entity); aerbunny string 0-2; moa feather 2-3. All with Looting.
- Tuning is in `static final` constants at the top of each class (`AetherMobs` has the spawn weights).

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
