# The Aether

The sky world: pale islands of holystone floating in an endless bright sky, mint grass and skyroot trees on top,
clouds you can walk into. Andy asked for it to be HEAVILY based on The Aether mod, so the names, shapes, numbers and
feel follow it; every line of Java and JSON is ours and every texture is painted by `tools/gen_aether.py` (clean room:
behaviour only, nothing copied). The dimension is `jaymospowers:aether`. The way in is Wind's (`power/wind/aether/WindAether`),
there is no portal. All numbers are `static final` constants at the top of the class named in brackets.

This page is the blocks and items (Track A1). Worldgen and sky are track A2, mobs C/D (see the design notes).

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
feature `jaymospowers:skyroot_tree` (made by the dimension's worldgen, track A2; until it exists the sapling just
keeps its stage).

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
sapling once the dimension's tree feature exists.
