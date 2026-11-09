---
status: running
task: waterskin-tiers
branch: claude/waterskin-tiers
with:
pr:
mods: kitchen
versions:
started: 2026-11-08
owns:
  - kitchen/src/main/java/com/jaymoskitchen/climate/WaterskinItem.java
  - kitchen/src/main/java/com/jaymoskitchen/climate/Thirst.java
  - kitchen/src/main/java/com/jaymoskitchen/climate/Climate.java
  - kitchen/tools/gen_climate.py
  - kitchen/src/main/resources/assets/jaymoskitchen/lang/en_us.json
  - kitchen/src/main/resources/data/jaymoskitchen/recipe/waterskin.json
  - kitchen/src/main/resources/data/jaymoskitchen/recipe/waterskin_iron.json
  - kitchen/src/main/resources/data/jaymoskitchen/recipe/waterskin_dragon.json
  - kitchen/src/main/resources/assets/jaymoskitchen/models/item/waterskin.json
  - kitchen/src/main/resources/assets/jaymoskitchen/models/item/waterskin_iron.json
  - kitchen/src/main/resources/assets/jaymoskitchen/models/item/waterskin_dragon.json
  - kitchen/src/main/resources/assets/jaymoskitchen/items/waterskin.json
  - kitchen/src/main/resources/assets/jaymoskitchen/items/waterskin_iron.json
  - kitchen/src/main/resources/assets/jaymoskitchen/items/waterskin_dragon.json
---

# Waterskin Tiers

**Task:** Kitchen, thirst: the waterskin comes in tiers instead of one item. From the five-mods pick-list, row "Canteens with limited sips" (Simple Difficulty Reforged, MIT): "Leather holds 3 uses, iron 6, dragon 8 and cleans the water as it fills." Andy's note: it should be the existing `WaterskinItem` gaining tiers, not a second item.

## Plan
- [ ] Update WaterskinItem to support tiers (leather, iron, dragon) with different capacities
- [ ] Add tier property to Water data component
- [ ] Update Climate to register three separate items (waterskin_leather, waterskin_iron, waterskin_dragon)
- [ ] Update gen_climate.py to generate textures for all three tiers
- [ ] Add crafting recipes for iron and dragon tiers
- [ ] Update lang entries for all three tiers
- [ ] Update item models and item JSONs
- [ ] Update Thirst.drink calls to respect tier properties (dragon cleans water on fill)
- [ ] Build and test

## Notes
- Leather: 3 sips (current is 5, reducing to match source suggestion)
- Iron: 6 sips
- Dragon: 8 sips, cleans water as it fills
- The existing waterskin recipe produces leather waterskin
- Need to decide on materials for iron and dragon tiers

## Done