---
status: in-review
for: hermes
from: hermes (Andy's pick-list, 2026-11-08)
asked: 2026-11-08
thread: 1569144710264791172
claimed_by: hermes-a
pr: 1176
suggested_owns: [kitchen/src/main/java/com/jaymoskitchen/climate/WaterskinItem.java, kitchen/src/main/java/com/jaymoskitchen/climate/Thirst.java]
writer: hermes-writer
text_pass: done
---

# Waterskin tiers

Kitchen, thirst: the waterskin comes in tiers instead of one item.

## What's wanted

From the five-mods pick-list, row **Canteens with limited sips** (Simple Difficulty Reforged, MIT):
"Leather holds 3 uses, iron 6, dragon 8 and cleans the water as it fills."
Andy's note: it should be the existing `WaterskinItem` gaining tiers, not a second item.

## Notes

- Simple Difficulty is Forge 1.20.1, so this is our own code — borrow the shape, not the code or art.
- Deciding question for Andy: are the tiers separate items (leather waterskin / iron canteen) or one item whose capacity upgrades? His note says tiers of one item.
- Amounts are ours to pick; the source's 3/6/8 are only a suggestion.
- 2026-11-08 20:21 hermes-a's last run stopped before finishing; handed back as open so it can carry on.
- 2026-11-09 05:56 hermes-a's last text pass stopped before finishing; handed back so it can carry on.
- 2026-11-09 06:03 Text pass: added change note changes/kitchen/claude-waterskin-tiers.md (lang entries already clear: Leather Waterskin, Iron Canteen, Dragon Canteen; tooltip keys unchanged and correct)
- 2026-11-09 09:18 Review (head a510ed0): changes needed. 1) Climate.java:67-71 drops the item id jaymoskitchen:waterskin and registers three new ids, so every waterskin already in a player's inventory or chest vanishes on update. Andy asked for the existing WaterskinItem to gain tiers, not a replacement: keep jaymoskitchen:waterskin as the leather tier (same id, texture and both its recipes), and add the iron and dragon tiers next to it. 2) The raw-hide recipe is gone: waterskin_from_raw_hide.json was renamed into the iron recipe, and #jaymoskitchen:cords became plain string. Keep the hide and cords recipes; kitchen/tools/gen_hides.py:164 still generates that recipe for jaymoskitchen:waterskin. 3) Unrelated files: the five HUD sprites textures/gui/sprites/hud/thirst_*.png are changed, and libs/jaymos-kitchen-0.5.93.jar is committed. Take both out of this PR. 4) Version numbers in comments, which CLAUDE.md forbids: '(0.5.80)' at Climate.java:43 and WaterskinItem.java:30 (and 0.5.80 isn't even this version). 5) Small: test 'stack.getItem() instanceof WaterskinItem' instead of three is() calls (ClimateHud.java:63, Climate.java:129 and 139), drop the unused Tier.byId, and add the missing newlines at the ends of Climate.java, WaterskinItem.java and the recipe. The tier logic is right: 3/6/8 sips, the bar and tooltip use maxSips(), and the dragon canteen fills clean.
