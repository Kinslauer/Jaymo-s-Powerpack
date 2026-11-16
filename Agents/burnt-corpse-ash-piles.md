---
status: stalled
task: burnt-corpse-ash-piles
branch: claude/burnt-corpse-ash-piles
with:
pr:
mods: kitchen
versions: 0.6.31
started: 2026-11-16
owns:
  - kitchen/src/main/java/com/jaymoskitchen/corpse/CorpseEntity.java
  - kitchen/src/main/java/com/jaymoskitchen/corpse/Corpses.java
  - kitchen/src/main/java/com/jaymoskitchen/corpse/AshPileBlock.java
  - kitchen/src/main/resources/assets/jaymoskitchen/blockstates/ash_pile.json
  - kitchen/src/main/resources/assets/jaymoskitchen/models/block/ash_pile_1.json
  - kitchen/src/main/resources/assets/jaymoskitchen/models/block/ash_pile_2.json
  - kitchen/src/main/resources/assets/jaymoskitchen/models/block/ash_pile_3.json
  - kitchen/src/main/resources/assets/jaymoskitchen/models/block/ash_pile_4.json
  - kitchen/src/main/resources/assets/jaymoskitchen/models/block/ash_pile_5.json
  - kitchen/src/main/resources/assets/jaymoskitchen/models/block/ash_pile_6.json
  - kitchen/src/main/resources/assets/jaymoskitchen/models/block/ash_pile_7.json
  - kitchen/src/main/resources/assets/jaymoskitchen/models/block/ash_pile_8.json
  - kitchen/src/main/resources/assets/jaymoskitchen/models/item/ash_pile.json
  - kitchen/src/main/resources/assets/jaymoskitchen/textures/block/ash_pile.png
  - kitchen/src/main/resources/data/jaymoskitchen/loot_tables/blocks/ash_pile.json
  - kitchen/src/main/resources/assets/jaymoskitchen/lang/parts/ash_piles.json
  - changes/kitchen/burnt-corpse-ash-piles.md
---
# Burnt corpses leave ash piles

**Task:** when a corpse burns away on the ground (not in lava), leave a pile of ash where it lay, sized to the body (a zombie a small heap, a ghast or giant a big pile across several blocks), instead of only smoke and white ash particles. Lava burns leave nothing.

**Plan**
- New snow-style ash block (1-8 layers): AshPileBlock, registered as Corpses.ASH_PILE with its BlockItem and lang key.
- CorpseEntity.leaveAshPile: called from the burn branch (burnTicks >= BURN_TICKS) before discard(); skips lava.
- Place 1-5 ash blocks at the corpse's block and neighbours, layers 2-8 by body size, only on air over a solid block.
- Change note changes/kitchen/burnt-corpse-ash-piles.md.

**Notes**
- Ash piles don't melt in rain and aren't shovellable into an ash item yet: builder's call, flagged in the PR.

**Done**
- (none yet)

## Notes
- 2026-11-16 05:18 No sign of work for 30 min: marked stalled (whoever ran it stopped).
