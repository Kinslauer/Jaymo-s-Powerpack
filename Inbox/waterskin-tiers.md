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
