---
status: running
task: village-mods-6-small-structures
branch: claude/village-mods-6
with: 
pr: 
mods: kingdoms
versions: 
started: 2026-11-24
owns:
  - kingdoms/src/main/java/com/jaymoskingdoms/smalls/ (new package: tree structures, mazes, wayside inns, cairns, cave gardens)
  - kingdoms/src/client/java/com/jaymoskingdoms/client/MasonPreview.java (new)
  - kingdoms/src/main/resources/data/jaymoskingdoms/worldgen/structure/ and structure_set/ (new files only)
  - kingdoms/src/main/resources/assets/jaymoskingdoms/lang/parts/smalls.json (new)
  - changes/kingdoms/claude-village-mods-6.md
  - one-line hooks only in Rumours.java, JaymosKingdoms.java, PlacementReasons tags
---
# Village mods patch 6: Small structures

**Task:** build every pick of patch 6 (docs/VillageMods-Plan.md): tree structures, mazes, wayside inns, cairn caches, cave gardens, and the mason's table design preview.

## Plan
- Orientation scan (six scanners) of the hook points, then build each row as its own class.

## Notes
- Patch 4 worker owns BoardWork, TownNotices, Economy, decrees: not touched here.

## Done
What a player will see, and what wasn't tested.
