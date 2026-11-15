# continental world gen - great continents, wide oceans, a climate map
- **Continents**: the Overworld's land now forms a few great continents, most several thousand blocks across, with open
  ocean between them, often over 1,500 blocks from any shore. About 30% of the world is land (it varies by seed), and over 90% of it is in the
  continents. Coasts are ragged, with capes, bays and islets near shore, and a narrow shelf of shallow sea before the
  deep ocean. Rare islands rise out on the open sea. The land round the world's origin is lifted so the spawn stands on a
  continent. Mohknee, its crossing and its islands are unchanged. New shape: `jaymoskingdoms:continents/shape` (noises
  `continents/land`, `continents/isles`), used by `overworld/continents` as the home world's shape;
  `mohknee/home_continents` removed.
- **Land lifted with the higher sea**: the game's own offset (overworld, large biomes, amplified) is 8 blocks higher,
  the same as `HighSea.RISE`. Before, the +8 sea drowned every low plain and river flat, so the 25% of land left was
  in thousands of islets and pools (measured on seed 1234: 4,350 separate pieces in 24,000 blocks square, now 740, most of them
  shore rocks). Coasts, beaches and plains now stand at the sea as the game meant them.
- **Climate map** (Andy: "biomes and civilizations should follow a continental heatmap too"): heat now runs by
  latitude, cold to the north (-z) and hot to the south (+z), in bands that repeat like a globe's. A pole lies 6,000
  blocks north of the origin and the equator 6,000 blocks south. Continent-sized wobbles bend the bands, and a
  continent's middle is harsher than its coasts. Coasts are wet and interiors dry, with a dry desert belt on the
  equator side of the temperate band and a wet, jungle equator. The seas follow it too: frozen seas in the north,
  warm seas in the south. Snow, taiga, savanna, desert, badlands and jungle all now lie within about 5,000 blocks of
  spawn on the seeds checked (on seed 1234, deserts were 11,700 blocks away before). Settlements follow the biomes, so
  their buildings (Palette), dress (Climate), coin (Currencies) and heritage mix (VillagerHeritage), and heritage home
  villages (LifeStory), follow the map. New `continents/latitude`, `continents/temperature`, `continents/vegetation`;
  `mohknee/home_temperature` and `home_vegetation` removed (Mohknee keeps its own climate).
- Written by `tools/gen_continents.py` (reads the 26.3 jar for the game's offset files), then `tools/gen_mohknee.py`.
- Needs a new world: chunks made before it won't match new ones at the seam. Checked with the real generator on a dev
  server (land share, landmass and ocean sizes on three seeds, biome map 60,000 blocks across, a new world boots and
  spawns on land in 8-12 s; the biome map by land share and nearest of each to spawn on three seeds; /locate finds every
  village type, snowy at 650 blocks, desert at 6,800); not played.
