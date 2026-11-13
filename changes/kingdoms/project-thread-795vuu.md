# Mohknee, the blossom folk's continent, two days' sail east
- **Mohknee** (Blossom Lands plan, patch 1; Andy: "i would like all this to be on a seperate continent spoken about by
  villagers, and reached by LONG sailing", "it is called Mohknee and you can encounter tideborn islands on the way"):
  a round continent about 6,000 blocks across, in the Overworld, its middle at x 28,000, z 0. It is cool to mild
  high country with no desert, jungle or badlands: cherry groves all over it (13% of the land in a test world), with
  meadows, plains, flower forests, forests and rivers between.
- **The crossing**: 16,000 blocks of open deep sea on every side of it (two days and nights for an ordinary ship),
  beginning 7,500 blocks east of the world's origin. Eight small islands stand out in it, three of them on the way
  from the west, each level grassland about 350 blocks across with one village at its middle
  (`jaymoskingdoms:crossing_isle_village`, placed at exact spots through the island structure's new `only_at`), and
  no other village is started on them. A settlement on an island is the Tideborn's, as on any island.
- **Blossom settlements rise only on Mohknee** now (villages, towns and the castle). Cherry groves on the home
  continent stay as wild groves; Blossom villages already standing there are left alone. `/kingdom found blossom` and
  `blossomtown` still work anywhere for operators.
- **Every settlement on Mohknee is of the Blossom land** (minka, the Blossom tongue and names, ronin guards), whatever
  biome its bell stands in.
- **Nothing foreign is built there**: the home continent's villages and the other structure mods' surface buildings
  (towers, taverns, keeps) are not started on Mohknee. The game's own ruins, wrecks, mines and strongholds still are.
- Operators: `/kingdom mohknee` gives the bearing and distance to its shore; `/kingdom mohknee map [blocks per pixel] [reach]`
  paints what the world makes of the land and its sea to `mohknee.png` in the game folder (no chunks are loaded).
- How: no code shapes the land. `tools/gen_mohknee.py` writes the Overworld's `continents`, `temperature`,
  `vegetation`, `erosion` and `ridges` density functions (the game's own `distance_to_point`, `slice`, `lerp` and
  `clamp`; off the land and its islands they give exactly what they gave before) and `jaymoskingdoms:mohknee/*`;
  `seas/Mohknee` holds the same numbers for the game's rules (the tool checks they agree); `MohkneeStructureMixin`;
  `SharedPools.OWN_PLACES` keeps the islands' village set out of the shared village pool.
- `Offshore.island` (by the ground) now also looks 320 and 384 blocks out for sea, so an island up to about 700
  blocks across counts as one: a few more of the home seas' larger islands can take island villages and caches.
- Only new chunks take the new shape, so no new world is needed, but a world already explored more than 7,500 blocks
  east of the origin keeps what it has there, with a seam where old chunks meet new.
- Checked in a Kingdoms dev server (new world): the map shows the crossing 100% sea and the land and islands in
  place; `/locate` finds a village at each of the eight islands, none on Mohknee, and the home world's as before; one
  island generated for real had its village and bell. Not checked: the land walked in game, a Blossom village rising
  there, the island villages turning Tideborn, and the full pack's worldgen mods over it.
