---
status: done
task: village-mods-6-small-structures
branch: claude/village-mods-6
with: 
pr: 2110
mods: kingdoms
versions: 
started: 2026-11-24
owns:
  - kingdoms/src/main/java/com/jaymoskingdoms/ new classes: Cairns, TreeSites, Crow, HedgeMaze, CryptMaze, MazeGen, WaysideInns, CaveGardens
  - kingdoms/src/client/java/com/jaymoskingdoms/client/ new classes: MasonPreview, CrowRenderer
  - small edits: Rumours, PlacementReasons, Hermits, SeatCompounds, Bounties (courtAt fallback), MasonTable (one public accessor), JaymosKingdoms and JaymosKingdomsClient (register lines)
  - tools/small_structures.py, tools/small_designs/
  - kingdoms data: structure/smalls/, worldgen/*/smalls/, tags/worldgen/biome/has_structure/smalls/, new structure tags, loot tables cairn, hollow_oak, crypt_maze, cave_garden
  - lang parts: cairns, tree-sites, wayside-inns
  - changes/kingdoms/claude-village-mods-6.md
---
# Village mods patch 6: Small structures

**Task:** build every pick of patch 6 (docs/VillageMods-Plan.md): tree structures, mazes, wayside inns, cairn caches, cave gardens, and the mason's table design preview.

## Plan
- Fixed designs drawn once in tools/small_structures.py (one file per row in tools/small_designs/), which writes the templates, worldgen JSON and the preview pictures from the same blocks.
- Cairns first as a thin slice to green, then the other five rows built in parallel worktrees, each reviewed against the 26.3 sources by a second agent.

## Notes
- Patch 4 worker owns BoardWork, TownNotices, Economy, decrees: not touched (the inn's board uses BountyBoard, Bounties.boardUp and a one-line courtAt fallback).
- 2026-11-24: the "stalled" mark was the T3 server restart cancelling the build workflow; its worktrees had finished, and the work was merged after.
- Lore: five (new) paragraphs added to Lore.md before "Rules for writing against this" for Andy to approve (cairns, trees, mazes, inns, cave gardens).
- Previews: E:/Jaymo Vault Live/Assets/village-mods-6/ (13 pictures).

## Done
PR #2110 merged by Andy (squash, head 76520a4c9e). CI green. Players get cairn caches (rumours name the nearest), the hermit's hollow oak, the bandits' spruce lookout, the hanging tree and its crows, a hedge maze in some castle grounds, a changing stone maze under necromancers' crypts, an inn halfway along long King's Roads, druids' cave gardens, and an outline of a held plan's plot. Compiled by CI only; nothing generated or played in a world.
- 2026-11-24 20:02 PR #2110 is merged into master; moved to Agents/Done.
