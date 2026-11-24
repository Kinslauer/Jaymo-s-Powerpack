---
status: in-review
for: claude-cloud
from: coordinator
asked: 2026-11-24
thread: cmsg_017LSt6rWz6CzxSGvzDd41XW84yJWoLAAvdc9WzrgnCHQL
claimed_by: claude-cloud
pr: 2089
suggested_owns: []
---
# Reference Mods Patch 1: Felling: cut through the trunk, carved stump, collapse

Andy ticked these rows on the Reference Mods pick list (https://claude.ai/artifact/Fu1jDwywXNKSGj6mmU65fg, db picks/andy).
Full plan: Picklists/ReferenceMods-Plan.md, section "## Patch 1". Other mods' code and art are never copied
(ARR = remake ourselves). Any structure states where it generates and why (structure placement plan,
https://claude.ai/artifact/4foeFXysu1Vg9KJBADDZab). Change note per mod under changes/<mod>/<branch>.md.

## Patch 1: Felling: cut through the trunk, carved stump, collapse
Owns: kingdoms trees (FallingTree, ChoppedLog) + powers ground (CaveIns).
Merges with Inbox tree-chop-through-trunk.md (same ask): close that task into this one.

- **T1 Cut through the trunk to fell it** (Timber Tales, size M). The open felling task: each blow cuts a notch at the height you swing, deepening round the trunk. The tree only falls once nothing joins the top to the base, then our falling-tree physics takes over. Cheap: the check runs only when a cut finishes. We have: Felled, our TreeChop-style ChoppedLog chopping, FallingTree (#164), the hanging-tree fix (#1212). Inbox: tree-chop-through-trunk is open.
- **T2 A carved stump stays behind** (Timber Tales, size S). What's left is a stump block with the cut face and notch showing, not a full log. You can sit on it, dig it out with a shovel for a little wood, or leave it to rot into rich soil over a few seasons. We have: The stump is part of the same Inbox task.
- **T3 Cracks you can see and hear** (Timber Tales, size S). No bar on screen: cracks spread across the trunk as you cut, and the tree creaks and groans louder as it nears breaking, so you know when to step clear. We have: Logs break with the vanilla crack only.
- **T4 Choose which way it falls** (Timber Tales, size S). Cut the notch on the side you want it to fall, finish from the back, and it falls toward the notch, unless it leans badly the other way. Gets you a tree across a river as a bridge, or away from your roof. We have: Trees fall to the heavier side, or away from the cutter if even.
- **T5 The ground answers the fall** (Timber Tales, size S). A dust and leaf burst where it lands, a thump you feel (small screen shake close by), flattened grass and snow, birds bursting out of the canopy, and nearby animals bolting. We have: Falling trees damage what they hit and smash weak blocks (#1019).
- **T6 Woodcutters fell trees the same way** (Timber Tales, size S). Village woodcutters cut the notch and back-cut too, and call a warning bubble before it comes down. A tree half-cut by a woodcutter is a hazard you can walk under. We have: Woodcutters chop trees into their bags.
- **T7 Blasts bring buildings down** (TNT Logic Enhanced, size M). Dynamite, cannon shells, creepers and siege fire undercut a wall and the floors above come down as falling blocks. Our support beams hold what they prop. Siege camps can finally topple a tower. We have: CaveIns, loose wood falling (#1942), support beams.
- **T8 Chests fall with what's in them** (TNT Logic Enhanced, size S). A chest or barrel in a collapsing house rides down intact and lands in the rubble, so looting a ruined keep means digging.

## Folded in: Inbox/tree-chop-through-trunk.md

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
