---
status: running
task: Moog's small boats crewed by a realm's fishermen who sail home; talk screen's empty purple panel hidden
branch: claude/competent-cori-d667a2
with: Claude Code (Opus 5.5), side session of local_2bfd1f34
pr:
mods: kingdoms
versions:
started: 2026-11-22
owns:
  - kingdoms/src/main/java/com/jaymoskingdoms/SeaSites.java
  - kingdoms/src/main/java/com/jaymoskingdoms/IllagerSites.java
  - kingdoms/src/main/java/com/jaymoskingdoms/Fishers.java
  - kingdoms/src/client/java/com/jaymoskingdoms/client/mixin/mca/McaButtonColumnMixin.java
---
# Small boats are realm fishers; talk panel

**Task:** Andy (2026-11-22, clip at 7:45 PM): a boat at sea "spawned with no helm and two nitwits, they should be fishers
belonging to some land and out here to get it and go back. thats it." And the purple box at the top right of the talk
screen "needs to disappear when not in use. right now its layered behind the wheel".

## Plan
- Moog's `ship_small`: helm search reaches her deck; cast off as a FISHER ship of the nearest realm with a harbour,
  manned by its fishermen, fishing where she floats, then home (Fishers.think). Already-settled ones retrofitted.
- McaButtonColumnMixin: panel only round buttons still on the screen.

## Notes
- Cause 1: SeaSites.helm searched sea+1..sea+8, her deck stands at sea+9 (y 72 in the clip); people were a FISHERMAN
  visitor (job lost with no workstation) and a plain stranger (no job: Nitwit).
- Cause 2: MCA's Talk button calls clearWidgets() without setLayout, so the column list kept the old buttons.
