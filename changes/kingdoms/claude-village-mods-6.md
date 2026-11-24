# Village mods patch 6: small structures (cairns, tree sites, mazes, wayside inns, cave gardens, the plan's plot outline)
- **Cairn caches** (rc_cairn): heaps of field stones in four shapes on the hills, moors, taiga and plains, within about
  500 blocks of a settlement (they mark the ways out of it; `PlacementReasons` rule `cairns_by_ways`). Under each, an arm's
  length down, a chest of what travellers leave (`chests/cairn`: bread, torches, string, nuggets, now and then emeralds).
  Now and then a rumour names the nearest cairn instead of a treasure (`Cairns.hint`, from `Rumours.tell`).
  Templates are drawn by `tools/small_structures.py` (`structure/smalls/`), which also draws the preview pictures.
- Built; not played. Compiled by CI only.
