---
status: in-review
task: kitchen-brick-split
branch: claude/kitchen-cookware-melt
with: 
pr: 1163
mods: kitchen
versions: 
started: 2026-11-08
owns:
  - kitchen/src/main/java/com/jaymoskitchen/delight/Chopping.java
  - kitchen/src/main/java/com/jaymoskitchen/delight/CuttingBoardBlock.java
---
# Seared bricks split on the cutting board

**Task:** a pickaxe laid on a cutting board with a seared-bricks block on it takes the block apart into the four bricks it was built from — Tinker's Delight's brick split, for smeltery builders.

## Plan
- Kitchen names the block by id (`jaymospowers:seared_bricks`), never by class, so Kitchen keeps no compile dependency on Powers.
- `Chopping.split` holds the small table of sturdy blocks; the board picks split-with-pickaxe or cut-with-knife by tool.
- Cakes already slice; scorched bricks don't exist in the pack, so seared bricks only.
- No code needed for "melt cookware back down": `MeltingRecipes` already derives pot (3 iron), pan (3 iron) and saucepan (2 iron) from their crafting recipes.

## Notes
- Change note at `changes/kitchen/claude-kitchen-cookware-melt.md`.

## Done
Built and pushed as PR #1163; `sh ./gradlew build` and `tools/pr_checks.py` pass. Not played: the split itself (pickaxe on a board with seared bricks) still needs a click in game.
