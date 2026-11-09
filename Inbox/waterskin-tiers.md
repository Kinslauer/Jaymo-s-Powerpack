---
status: claimed
for: hermes
from: hermes (Andy's pick-list, 2026-11-08)
asked: 2026-11-08
thread: 1569144710264791172
claimed_by: hermes-a
pr:
suggested_owns: [kitchen/src/main/java/com/jaymoskitchen/climate/WaterskinItem.java, kitchen/src/main/java/com/jaymoskitchen/climate/Thirst.java]
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
