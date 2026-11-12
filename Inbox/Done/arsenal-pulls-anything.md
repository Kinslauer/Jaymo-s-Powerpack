---
status: done
for: claude-cloud
from: claude-cloud-lore-thread
asked: 2026-11-11
claimed_by: claude-cloud
pr: 1326
writer: hermes-writer
text_pass: done
---

# Pocket Arsenal pulls from anywhere

**Andy (2026-11-11):** "Arsenal can pull from literally anywhere, that is why he can grab guns from the future, items from players inventories, or even hazardous material that harms himself, until he masters the power he can't command it at will."

**Build:** widen Arsenal's pull table: future guns, an item from a nearby player's inventory (returned when it expires), hazards that hurt the user; random until mastered, chosen at will once mastered. Check what Reach Through already does first. Lore: Lore.md "The powers".

## Notes
- 2026-11-11 21:38 PR #1326 up. Question for Andy: should a mastered (ascended) Arsenal be able to take from a player's pack on purpose? For now it just stops happening.
- 2026-11-11 22:01 Andy answered: mastered Arsenal looks at a player and holds Reach to grab a random item. Added to PR #1326.
- 2026-11-11 22:10 Merged on Andy's word (5:10 PM ET). PR Merges post still needed.
- 2026-11-11 18:10 Text pass: reviewed PR 1326's new player text. 11 new keys in lang/parts/arsenal.json (pack_taken, pack_pulled, pack_returned, hazard.ember/puffer/wither/cactus/frost/charge, grab_holding, pack_empty) plus their call sites in ArsenalAnywhere.java and ArsenalPower.java, and changes/powers/claude-project-thread-4dzn49.md. All plain, in-world voice, short lines; every lang key and s / 1-s / 2-s placeholder kept; no rewrites needed.
- 2026-11-11 18:12 Correction: the unchanged placeholder formats are percent-s, percent-1-s and percent-2-s, kept verbatim with every lang key.
- 2026-11-12 02:01 PR #1326 is merged into master; moved to Inbox/Done.
