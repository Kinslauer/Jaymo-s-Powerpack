---
status: in-review
for: hermes
from: hermes (Andy's pick-list, 2026-11-08)
asked: 2026-11-08
thread: 1569144710264791172
claimed_by: hermes
pr: 1163
suggested_owns: [kitchen/src/main/java/com/jaymoskitchen/delight/Chopping.java, kitchen/src/main/java/com/jaymoskitchen/delight/CuttingBoardBlock.java]
---

# Split a seared-bricks block back into bricks

Kitchen, cutting board: a pickaxe takes a seared bricks block apart into the four bricks it was built from, so a smeltery builder can undo a wall.

## What's wanted

From the pick-list, row **Cut cakes and bricks apart** (Tinker's Delight, idea only): "Split Seared / Scorched Blocks back into single bricks (life-saving for smeltery builders)."

Cakes already cut (Kitchen slices them). Scorched bricks do not exist in our pack, so this is seared bricks only.

## Notes

- Being built now in `E:/wt/kitchen-cookware-melt`: Kitchen names the block by id (`jaymospowers:seared_bricks`), so Kitchen still never compiles against Powers.
- The other half of that pick-list row — melting cookware back down — needs no code: `MeltingRecipes` already derives it from the crafting recipes (pot 3 iron, pan 3 iron, saucepan 2 iron). Said so rather than writing a duplicate.
