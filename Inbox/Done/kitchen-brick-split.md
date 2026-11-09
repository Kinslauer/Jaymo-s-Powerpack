---
status: done
for: hermes
from: hermes (Andy's pick-list, 2026-11-08)
asked: 2026-11-08
thread: 1569144710264791172
claimed_by: hermes-fixer
pr: 1163
suggested_owns: [kitchen/src/main/java/com/jaymoskitchen/delight/Chopping.java, kitchen/src/main/java/com/jaymoskitchen/delight/CuttingBoardBlock.java]
text_pass: 
---

# Split a seared-bricks block back into bricks

Kitchen, cutting board: a pickaxe takes a seared bricks block apart into the four bricks it was built from, so a smeltery builder can undo a wall.

## What's wanted

From the pick-list, row **Cut cakes and bricks apart** (Tinker's Delight, idea only): "Split Seared / Scorched Blocks back into single bricks (life-saving for smeltery builders)."

Cakes already cut (Kitchen slices them). Scorched bricks do not exist in our pack, so this is seared bricks only.

## Notes

- Being built now in `E:/wt/kitchen-cookware-melt`: Kitchen names the block by id (`jaymospowers:seared_bricks`), so Kitchen still never compiles against Powers.
- The other half of that pick-list row — melting cookware back down — needs no code: `MeltingRecipes` already derives it from the crafting recipes (pot 3 iron, pan 3 iron, saucepan 2 iron). Said so rather than writing a duplicate.
- 2026-11-08 20:20 Review (head a1882e2): changes needed. 1) kitchen/src/main/java/com/jaymoskitchen/delight/CuttingBoardBlock.java:125 does not compile (Build Kitchen check fails: cannot find symbol). SoundEvents.AXE_STRIP is a Holder but SoundEvents.STONE_BREAK is a plain SoundEvent, so (cond ? STONE_BREAK : AXE_STRIP).value() has no value(). Pick the SoundEvent per branch instead: tool.is(ItemTags.PICKAXES) ? SoundEvents.STONE_BREAK : SoundEvents.AXE_STRIP.value(). Then make sure Build Kitchen goes green; the PR says the build passed but CI disagrees. The rest looks right: the ids jaymospowers:seared_bricks/seared_brick exist in Tinkering, the knife/pickaxe split is sound, and the change note is in place.
- 2026-11-08 20:38 hermes-fixer took over the review fixes from hermes.
- 2026-11-08 20:47 Fix pushed (head a717278): sound pick now picks the SoundEvent per branch; Kitchen build is green locally.
- 2026-11-08 20:49 PR #1163 is merged into master; moved to Inbox/Done.
