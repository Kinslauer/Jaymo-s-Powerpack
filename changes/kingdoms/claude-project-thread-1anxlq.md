# Stormborn Tomb: the stone-filled halls and the trial chamber rooms, fixed for real
- **Fault 1, halls full of stone:** the tomb's halls came from DungeonZ's dungeon dimension, which is empty: its chunk
  generator places nothing. The builder saved many rooms and caverns as structure void (no block) rather than air,
  which was the same thing there. In the overworld a void cell keeps the ground, so those rooms came out packed with
  stone, ores and all: right_hallway's hall, right_split's great cavern, split's chasm, corners of the boss arena and
  first_hallway. `kingdoms/tools/tomb_caverns.py` now writes air into every such cell: it lays the pieces out as
  `StormbornTomb.LAYOUT` does, and a void cell is a room's when the tomb's blocks stand round it on all four sides
  along its level and over or under it, and it joins the tomb's own air. 28,545 cells in 6 templates. The barrow,
  parkour and descent were built for the overworld (their voids are meant to be ground) and are left alone.
- **Fault 2, trial chamber rooms inside it:** nothing kept vanilla's trial chambers (one start every 34 chunks or so)
  away from a tomb 270 blocks across, so they often overlapped it and cut their rooms through its halls. A structure in
  the new tag `jaymoskingdoms:gives_way_to_stormborn_tomb` (trial chambers and ancient cities) now doesn't generate
  within 16 chunks of a tomb that will generate (`StormbornTomb.near`, worked out the way the game decides it: the
  set's spread, the start chunk's turn, depth and the biome under the barrow; `StructureGivesWayMixin`). Only those
  near a real tomb are lost.
- **#1254 undone:** it pointed the pieces at templates that don't exist (`stormborn_tomb_<part>`, the files are
  `stormborn_tomb/<part>`) and lifted every piece to the ground height over it, so tombs since then came out broken or
  empty. The placement is back as it was. It also took out the Moon/Sun registration (`SunMoon.register`,
  `SunMoonClient.register`): the Moon's phases ticking, the Sun's glare and clicking the Sun work again.
- **Unused pools gone:** the template pools #1137/#1218 added (`worldgen/template_pool/stormborn_tomb/`,
  `overworld_stormborn_tomb.json`) were never read, since the tomb places its pieces itself; deleted.
- Tombs and trial chambers already generated keep what they have. Chunks not yet generated get the fixed tomb.
- Built; checked on a dev server with `/place` (see the PR). Not played.
