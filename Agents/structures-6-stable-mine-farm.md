---
status: stalled
task: structures-6-stable-mine-farm
branch: claude/structures-6-stable-mine-farm
with: 
pr: 
mods: kingdoms
versions: 
started: 2026-11-26
owns:
  - kingdoms/src/main/resources/data/jaymoskingdoms/structure/smalls (stable, mine, farm NBTs)
  - kingdoms/src/main/resources/data/jaymoskingdoms/worldgen/{structure,structure_set,template_pool}/smalls
  - kingdoms/src/main/resources/data/jaymoskingdoms/tags/worldgen/structure/{stables_by_towns,mines_by_buyers,farms_by_homes}.json
  - tools/small_designs (stable, mine, farm designs)
---

# Structures 6: stable, mine and farm

**Task:** Build three original worldgen structures (a stable, a mine, a farm) for Kingdoms and place them by track 2's PlacementReasons rules: stables by towns, mines by buyers, farms by homes. Tied to the climate heatmap; worlds stay procedural. Reference pictures: a chimney house with a horse (stable), a fenced open quarry (mine), a farm screenshot — recover before finalizing designs.

## Plan
- Read tools/small_designs conventions (cairn, trees) and tools/small_structures.py output format.
- Write design files for stable/mine/farm (original builds, 26.3 blocks).
- Run tools/small_structures.py to emit NBTs + worldgen JSONs.
- Add the structures to the stables_by_towns / mines_by_buyers / farms_by_homes structure tags and biome tags.
- Build kingdoms, run pr_checks.py, commit, push, open PR.

## Notes
- Track 2 (PlacementReasons) is merged as #2085; rules stables_by_towns (19 chunks of a settlement), mines_by_buyers (9-25 chunks), farms_by_homes (6 chunks) already exist.
- Patch 6 precedent (PR #2110): data-only, no Java changes.

## Done
(none yet)
- 2026-11-26 19:32 No sign of work for 30 min: marked stalled (whoever ran it stopped).
