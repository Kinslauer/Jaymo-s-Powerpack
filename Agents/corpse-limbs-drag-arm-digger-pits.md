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
- 

## Done

