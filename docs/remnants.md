# The Aether's remnants: monoliths and Giants (Kingdoms 0.7.145)

## The lore (Andy, 2026-10-20)
The Aether scattered remnants of itself across the Overworld. The last surviving pieces of that puzzle are the
monoliths and their Giants. Each Giant carries the element of one of the Aether II's four highlands: the
**Highfield Giant** (the sky-meadows), the **Magnetic Giant** (the lodestone lands), the **Arctic Giant** (the frozen
peaks) and the **Irradiated Giant** (the Ambrosium-sick forests). Each rises beside a monolith, and the first Giant to
fall in a world raises the world tier.

## Why it's a remake
Neither mod runs in the pack: Ancient Remnants: Monoliths (Obscuria, Obscuria Modding License) stops at Minecraft
26.1.x (its version feed's newest is 1.3.0 for 26.1.2), and The Aether II is NeoForge only (26.1.2 alpha). Andy chose
to remake the monoliths in Kingdoms. Following the clean-room rules of the REA prompts
(https://github.com/JoasASantos/REA-Prompts, section 4: extract behaviour, never code or assets), only what the mods
*do* was studied; every block, line of code and pixel here is our own.

What was taken from each, as behaviour only:
- **Ancient Remnants** (its public source, read only): monoliths are surface structures (jigsaw, `surface_structures`
  step, `project_start_to_heightmap: WORLD_SURFACE_WG`), each design tied to a set of biomes, spread out by a
  configurable random spread; its three monoliths are `warrior_monolith`, `hunter_monolith`, `sentinel_monolith`
  (`common/src/main/resources/data/ancient_remnants/worldgen/structure/`). A core (its Elderheart) sits in each.
- **The Aether II**: only the four highland names and their feel (Highfields green and windy, Magnetic with floating
  lodestone pillars, Arctic frozen, Irradiated glowing with Ambrosium). Nothing of its code or art.

Differences that remain from the originals (on purpose):
- Our monoliths are raised by code near players in the wild (like Kingdoms' treasure wrecks), not by worldgen: no
  datapack structures, so they work in old worlds too. They are a broken pillar (5x5 foot, 3x3 shaft, 1x1 crown,
  78-102 blocks to the crown since 0.7.458) with a floating heart, not the mod's designs.
- (0.7.458) The heart moves like the Elderheart does (behaviour only): `MonolithHeart` floats seven block displays of
  the element's gem, a core and six crystal shards on its sides, tumbling on all three axes while the shards breathe
  out and in, with motes drawn in off the stone round it. When the Giant falls the core goes out and the shards hang dark.
- There are no blessings, codex or research scraps (Ancient Remnants' rewards); a monolith's reward is its Giant.
- The elements follow the biome: cold biomes Arctic, mountains/hills/badlands Magnetic, swamps/jungles Irradiated,
  everything else Highfield (`Element.of`).

## How it works (`kingdoms/.../remnants/`)
- `Monoliths`: every 2 minutes each player out in the wild (as for wilderness encounters: on the surface, 256+ blocks
  from any settlement) has a 6% chance of a monolith rising 64-112 blocks away, if no monolith is within 1200 blocks.
  They're remembered on the Overworld (`monoliths` attachment). A player within 40 blocks of an awake monolith wakes
  its Giant (if it isn't already about): it rises 9-14 blocks away with a roar, keeps within 28 blocks of its monolith
  and never despawns. When it dies the monolith's core goes out for good.
- `AetherGiant`: 320 health, 10 armour, 4 toughness, 14 damage; a ground stamp (12 damage, thrown) every 8-12 s; its
  element's power every 6 s (3 s when it rages below 30% health); boss bar in its element's colour; takes no fall
  damage. Highfield: gust that hurls everyone up and heals on grass/moss. Magnetic: drags everyone in, harder per piece
  of metal armour, and its blows slow the armoured. Arctic: freezes those near it (powder-snow freezing), its breath
  gives Slowness III. Irradiated: poisons those near it; its flare poisons all near and withers the closest.
- World tier: the first Giant to fall broadcasts the news and raises the world's **floor** tier to Borderlands
  (`Monoliths.FIRST_GIANT_TIER`). Every player below it is lifted to it (now and when they join), and nobody can go
  below it. Each Giant also counts as a great foe for everyone's own tier progress.
- `AncientRemnantsCompat`: if Ancient Remnants is ever loaded (a 26.3 build), its monoliths are found by structure id
  and wake Giants too. It only reads structure ids; nothing of the mod is changed.
- Ops: `/kingdom monolith raise [highfield|magnetic|arctic|irradiated]` (12 blocks ahead), `/kingdom monolith list`,
  `/kingdom tier floor <tier>` (sets the floor; lowering it too).

## Art
- Giant model `client/remnants/GiantModel` (128x128 texture, drawn at 2.5x), skins painted by
  `kingdoms/tools/gen_giants.py` (`python3 tools/gen_giants.py` from `kingdoms/`). Previews in the project files under
  `art-overhaul/aether-giants/`.

## Not yet seen in game
Built but not compiled or played: the cloud can't reach Mojang's or Fabric's servers, so `./gradlew build` and
`genSources` need the home PC. Check first: the Giants' size and model, the stamp and powers, the boss bar, a
monolith rising (`/kingdom monolith raise`), the first-kill broadcast and floor.
