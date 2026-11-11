---
status: done
task: 
branch: claude/project-thread-qj39dp
with: 
pr: 1292, 1297 (both merged)
mods: kitchen, powers
versions: 
started: 2026-11-11
owns:
  - kitchen/src/main/java/com/jaymoskitchen/cooking/Step.java
  - kitchen/src/main/java/com/jaymoskitchen/kitchen/MinigameRecipe.java
  - kitchen/src/main/java/com/jaymoskitchen/delight/Chopping.java
  - kitchen/src/main/resources/data/jaymoskitchen/tags/item/cooking/
---
# Meat groups: crafting routes for every meat

**Task:** Andy wants every item to have a sensible crafting route, starting with meats: venison should go into dishes, by a tag or a universal meat item.

## Plan
- Item tags `jaymoskitchen:cooking/<group>` that a cooking Step accepts beside its own item.

## Notes
- Cube Animals' raw_venison went into no dish; Venison Stew only took Naturalist's.
- A grep scan lists 436 item ids (Powers 224, Kingdoms 86, Kitchen 126) that no recipe, loot or tag JSON names. Most are made in code (minigame dishes, brews, Tinkers parts), so each needs a look; not done here.

## Done
Any red meat, pork, poultry, mutton, small game, white or oily fish goes into the dishes that asked for beef, porkchop, chicken, mutton, rabbit, cod or salmon; the cutting board cuts by the same groups. Compiled, not played.

## 2026-11-11, second pass
- PR #1292 merged (Andy: "Merge and continue").
- Went through the flagged items: Kitchen 16, Kingdoms 86 and the Powers families. All have routes in code (forge parts, casts, quests, trades, drops, "never made" loot armour) except the four knife metals.
- PR #1297: Cobalt Ore in the Nether + smeltery alloys for Manyullyn, Queen's Slime, Hepatizon. Andy has an open choice on whether a Nether ore is the route he wants. Owns tools/gen_knife_metals.py and the knife-metal lines in tinkering/Tinkering.java, smeltery/Molten.java, smeltery/Melting.java.
- 2026-11-11 16:38 PR #1292 is merged into master; moved to Agents/Done.
