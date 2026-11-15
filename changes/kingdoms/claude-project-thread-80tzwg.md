# continental world gen - great continents, wide oceans
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
- Written by `tools/gen_continents.py` (reads the 26.3 jar for the game's offset files), then `tools/gen_mohknee.py`.
- Needs a new world: chunks made before it won't match new ones at the seam. Checked with the real generator on a dev
  server (land share, landmass and ocean sizes on three seeds, biome map 60,000 blocks across, a new world boots and
  spawns on land in 8 s); not played.
