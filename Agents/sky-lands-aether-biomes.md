---
status: stalled
task: sky-lands-aether-biomes
branch:
with:
pr:
mods:
versions:
started: 2026-11-15
owns:
  - E:/Jaymo Vault Live/Inbox/sky-lands-aether-biomes.md
---

# sky-lands-aether-biomes

**Task:** Research what biomes the sky-lands mod adds and how they're structured, for potential adaptation to the Aether dimension in Powers/Kingdoms.

## Plan
- Visit the sky-lands Modrinth page and extract biome information
- Document biome types, themes, and any unique features
- Compare with existing Aether biomes in Powers/Kingdoms
- Provide summary for Andy's review

## Notes
- Research only — no code changes yet
- Source: https://modrinth.com/mod/sky-lands
- GitHub: https://github.com/DeepCanionStudio/SkyLandsForge (MCreator-based, Forge 1.20.1)

## Done
### Sky Lands mod — biome summary (v0.6.1, Forge 1.20.1)

**Core sky island biomes (original):**
- **Golden Islands** — warm, cherry grove variant added in 0.5.0 (Floating Cherry Grove)
- **Glaciated Islands** — cold, floating swamp variant added in 0.5.0 (Floating Swamp)
- **Lavic Islands** — volcanic/lava-themed
- **Arid Islands** — desert-themed

**Version 0.6.0 "The Abyss" — Underground Dimension (y 0–384, inspired by TotK Depths):**
- **The Underground** — base biome: sulfur spikes, celestial trees, water deltas
- **Abyssal Forest** — fossils, giant celestial trees
- **Abyssal City** — massive skyscraper complex, contains the Smithing Template "Upgrade Of The Sky"

**Key mechanics worth noting for Aether adaptation:**
- **Vertical biome layering**: Surface sky islands → underground dimension (distinct dimension, not just deeper)
- **Glider with stamina** + "Light Weight" enchantment for traversal
- **Custom cloud system**: optimized particles, clumped spawning, fade in/out, higher Y range in sky biomes
- **Tool tier above netherite**: "Sword/Pickaxe/Axe/Shovel of the Sky" + infusable variants (Verdant/Pearlescent/Pale/Inky)
- **Structures**: Island clusters, archipelagos, great sky islands, floating dungeons, towers, lighthouses, celestial roots
- **Reward dispensers** per biome type (Glaciated, Lavic, Arid, Swampy)
- **New mobs**: Artificial Aracnid (underground), Happy Slobert (tameable, floating swamp)

### Existing Aether biomes in Powers (20 biomes, from `tools/gen_aether_world.py`)

**Zones by climate (temperature/humidity/erosion/weirdness/depth):**
| Zone | Biomes | Theme |
|------|--------|-------|
| Arctic (cold) | frozen_lakes, sheer_tundra, enduring_woodland, frigid_sierra | Ice/snow |
| Highfields (temperate) | shimmering_basin, flourishing_field, verdant_woods, shrouded_forest, **hanging_roots**, **mistsea**, **gilded_ridge**, **sunroot_grove** | Lush, varied |
| Irradiated (hot) | battleground_wastes, contaminated_jungle | Wasteland/jungle |
| Magnetic (high erosion) | magnetic_scar, violet_highwoods, turquoise_forest, glistening_swamp, **rustworks** | Metallic/magical |
| Special | hestveil_caverns (deep), expanse (generic) | Caves, fallback |

**The 4 "sky biomes" (added 0.20.96, cut from weirdness edges):**
- `hanging_roots` (low weirdness, dry-wet highfields)
- `mistsea` (high weirdness, mist/fog + white_ash particles)
- `rustworks` (low weirdness, magnetic)
- `gilded_ridge` / `sunroot_grove` (high weirdness, highfields, split by humidity)

### Comparison & adaptation ideas for Andy

| Sky Lands concept | Potential Aether adaptation |
|-------------------|----------------------------|
| **Golden/Glaciated/Lavic/Arid island variants** | Could expand Highfields zone with temperature-based island variants (already has some via humidity/weirdness) |
| **Underground "Abyss" dimension** | Aether already has `hestveil_caverns` at depth; could deepen into a full sub-dimension (like the Cloud Sea concept in MODLOG) |
| **Glider + stamina** | Aether has Cloud Skiff item; could add stamina/enchantment layer |
| **Custom cloud particles** | Aether has `CloudSeaWind` + `AetherVeil`; sky-lands' optimized clumped clouds could inform improvements |
| **Tool tier > netherite** | Aether has zanite/gravitite; sky-lands' infusable "of the Sky" tools are a nice progression twist |
| **Reward dispensers per biome** | Aether dungeons/ruins have loot; biome-specific dispensers could add variety |
| **Floating cherry grove / swamp variants** | Aether's `sunroot_grove` (wet gilded_ridge) and `mistsea` are analogous; could add more "floating X" sub-biomes |
| **Celestial wood/trees** | Aether has skyroot trees; celestial wood is a distinct aesthetic (blue/gold) |

**Bottom line:** Sky Lands is a Forge mod (MCreator) with a different structure (separate underground dimension). The Aether in Powers is a single noise-based dimension with 20 biomes across climate zones. The most portable ideas are: biome variants within zones, cloud particle improvements, glider/skiff stamina mechanics, and biome-specific structure loot. The "Abyss" underground dimension aligns with Andy's noted "Cloud Sea" concept (MODLOG #4335) — a cloud/air layer between Overworld and Aether.

*Compiled 2026-11-15; ready for Andy's review.*
- 2026-11-15 19:22 No sign of work for 30 min: marked stalled (whoever ran it stopped).
