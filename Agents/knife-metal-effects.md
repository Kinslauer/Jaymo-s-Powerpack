---
status: done
task: knife-metal-effects
branch: claude/knife-metal-effects
with: 
pr: 
mods: powers
versions: 
started: 2026-11-09
owns:
  - src/main/java/com/jaymospowers/tinkering/TinkerMaterial.java
  - src/main/java/com/jaymospowers/tinkering/Tinkering.java
  - src/main/java/com/jaymospowers/tinkering/ToolType.java
  - src/main/java/com/jaymospowers/tinkering/Trait.java
  - src/main/java/com/jaymospowers/tinkering/TraitEffects.java
  - src/main/resources/assets/jaymospowers/lang/parts/tinkering.json
  - src/main/resources/assets/jaymospowers/items/cobalt_ingot.json
  - src/main/resources/assets/jaymospowers/items/hepatizon_ingot.json
  - src/main/resources/assets/jaymospowers/items/manyullyn_ingot.json
  - src/main/resources/assets/jaymospowers/items/queen_slime_ingot.json
  - src/main/resources/assets/jaymospowers/models/item/tinkering/cobalt_ingot.json
  - src/main/resources/assets/jaymospowers/models/item/tinkering/hepatizon_ingot.json
  - src/main/resources/assets/jaymospowers/models/item/tinkering/manyullyn_ingot.json
  - src/main/resources/assets/jaymospowers/models/item/tinkering/queen_slime_ingot.json
  - src/main/resources/assets/jaymospowers/textures/item/tinkering/cobalt_ingot.png
  - src/main/resources/assets/jaymospowers/textures/item/tinkering/hepatizon_ingot.png
  - src/main/resources/assets/jaymospowers/textures/item/tinkering/manyullyn_ingot.png
  - src/main/resources/assets/jaymospowers/textures/item/tinkering/queen_slime_ingot.png
  - changes/powers/knife-metal-effects.md
---
# Knives carry their metal's effect

**Task:** Make tinkered knives (cobalt, slimesteel, manyullyn, queen's slime, hepatizon) apply their metal's effect on hit (poison / slow / weaken / burn / wither) at a low chance.

## Plan
- Carry on the prior hermes-b run (uncommitted in E:/wt/knife-metal-effects): review, fix the TraitEffects `level` redeclare compile error, add missing ingot models + textures
- Commit, build, pr_checks, open Powers PR, set in-review

## Notes
- One project only (Powers): the per-metal on-hit effect. Chopping.java (Kitchen) is existing cutting-board behavior, not the new part.
- Slimesteel already existed as a material; it now also leaves its mark (slowness) via metalMark.
- Don't touch version= or MODLOG (the release step does that); add changes/powers/knife-metal-effects.md instead.

## Done
What a player will see, and what wasn't tested.
