---
status: running
task: village-mods-6-small-structures
branch: claude/village-mods-6
with: 
pr: 2110
mods: kingdoms
versions: 
started: 2026-11-24
owns:
  - kingdoms/src/main/java/com/jaymoskingdoms/ new classes: Cairns, TreeSites, Crow, HedgeMaze, CryptMaze, WaysideInns, CaveGardens
  - kingdoms/src/client/java/com/jaymoskingdoms/client/ new classes: MasonPreview, CrowRenderer
  - small edits: Rumours.java, PlacementReasons.java, Hermits.java, SeatCompounds.java, MasonTable.java (one public accessor), JaymosKingdoms.java and JaymosKingdomsClient.java (register lines only)
  - tools/small_structures.py, tools/small_designs/
  - kingdoms data: structure/smalls/, worldgen/*/smalls/, tags/worldgen/biome/has_structure/smalls/, new structure tags, new loot tables
  - lang parts: cairns, tree-sites, mazes, wayside-inns, cave-gardens, mason-preview
  - changes/kingdoms/claude-village-mods-6.md
---
# Village mods patch 6: Small structures

**Task:** build every pick of patch 6 (docs/VillageMods-Plan.md): tree structures, mazes, wayside inns, cairn caches, cave gardens, and the mason's table design preview.

## Plan
- Fixed designs drawn once in tools/small_structures.py (one file per row in tools/small_designs/), which writes the templates, worldgen JSON and the preview pictures from the same blocks.
- Cairns first as a thin slice to green (draft PR #2110), then the other five rows built in parallel worktrees and reviewed.

## Notes
- Patch 4 worker owns BoardWork, TownNotices, Economy, decrees: not touched here (the inn's board uses BountyBoard/Bounties.boardUp only).

## Done
What a player will see, and what wasn't tested.
