---
status: running
task: Andy's clip 2026-11-23 9:53 AM (sheep limbs float, no drag arm, diggers take bodies)
branch: corpse-limbs-drag-arm-digger-pits
with: Claude (side session of the project thread)
pr: 
mods: kitchen, kingdoms
versions: 
started: 2026-11-23
owns:
  - kitchen/src/main/java/**/corpse client poses (CorpseRenderer, DragPose, CarryPose, ragdoll/*)
  - kingdoms Digger AI
---
# Corpse limbs, drag arm, diggers take bodies

**Task:** Andy's clip: a carried/dragged dead sheep's legs float apart from its body; there's still no arm visibly
holding the dragged body; diggers should drag corpses near their pits down into the hole (to feed or butcher).

## Plan
- Pull frames from the clip, find why quadruped legs detach, fix.
- Make the player's arm reach and hold the grip point while dragging (first and third person).
- Digger behaviour: take a nearby corpse into the pit.

## Notes
- Stopped by Andy before any code was written ("ill have another agent pick it up"). Worktree and branch removed; no PR.
- Found so far, for whoever picks it up: the earlier drag-arm ask isn't worded as such in Decisions.md (closest: 2026-11-22
  meat-hook note, "use both arms when lifting anything other than small animals"); Kitchen MODLOG 1311 (`CarryPose`
  ONE_HAND) and 2600 (hold use drags by the legs). Diggers in Lore.md line ~2586 ("small kobold-kin ... hidden pits");
  "The brood's larder" (~2244) says diggers won't take from a spider larder. Client classes: `DragPose`, `CarryPose`,
  `Limp`, `ragdoll/LooseParts`, `RagdollPose`, `DragSim`, `FirstPersonHandsMixin`.

## Done
Nothing built.

