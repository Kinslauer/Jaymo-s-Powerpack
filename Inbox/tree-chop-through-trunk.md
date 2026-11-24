---
status: open
for: any
from: Andy (Claude chat, 2026-11-23)
asked: 2026-11-23
claimed_by: 
pr: 
---

# Felling a tree: cut through the trunk, leave a carved stump

## What's wanted

Andy: "the tree chop should work in the same way, you must cut through the trunk, so the tree is no longer connected,
then it leaves a carved stump."

- A tree comes down only once the trunk is cut through: chopping takes the trunk apart at the height you're cutting
  (a notch that deepens around the trunk with each blow), and only when nothing connects the upper tree to the base does
  it fall (our physical falling trees, PR #164, and the hanging-tree fix, PR #1212).
- What's left in the ground is a carved stump (a new stump block or blocks with the cut face and notch visible), not a
  full log block.

## Notes

- "In the same way" points at a reference Andy had open (a clip or a mod) when he wrote this; ask him for it before
  designing the cut, and take ideas only unless its licence allows more.
- Check the pack's felling mods and our own felling (grep `docs/codemap/*.md` for fell, chop, stump, trunk) so this
  replaces the current rule cleanly and doesn't fight another mod's tree capitator.
- Cheap on the Deck server: the connectivity check runs only when a cut finishes a ring, not every tick.
- 2026-11-24 17:12 Folded into Inbox/refmods-01-felling.md (Reference Mods patch plan, 2026-11-24).
