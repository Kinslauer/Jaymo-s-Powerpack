---
status: in-review
task: Camp guards (pirates, raiders) dress from their own pack, not MCA's iron guard set
branch: camp-guards-own-gear
with: Claude Code (desktop)
pr: 1985
mods: kingdoms
versions: 
started: 2026-11-22
owns:
  - kingdoms/src/main/java/com/jaymoskingdoms/Ranks.java
  - kingdoms/src/main/java/com/jaymoskingdoms/McaBridge.java
  - kingdoms/src/main/java/com/jaymoskingdoms/mixin/mca/McaEquipmentMixin.java
---
# Camp guards keep their camp's gear

**Task:** Andy's clip (2026-11-22): the pirates on the Illager Structures balloon wore full plate. Every camp guard should be
MCA and follow the Guard Wardrobe (Raiders column), not MCA's own guard armour.

## Plan
- MCA's EquipmentTask fills an empty pack slot from its guard equipment set (iron). A camp's villager wears only what is in
  their pack (the camp's gear copied over when the folk became MCA).
- The folk's raider harness pieces (torn hood, cloak, horned crest) carry over when the folk becomes MCA.

## Notes
- The balloon is template blocks (IllagerSites `illager_balloon` / `balloon_tower`), not a ship: it never moves.

## Done
PR #1985. Camp raiders and pirates who became MCA wear only their pack's gear (no MCA guard iron); their raider harness
pieces carry over. Not built locally (CI builds it); not played.
