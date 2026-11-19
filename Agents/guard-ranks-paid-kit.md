---
status: in-review
task: Give guards ranks and paid-for armour
branch: claude/project-thread-t799au
with: cloud thread
pr: 1791
mods: kingdoms
versions: 
started: 2026-11-19
owns:
  - kingdoms/src/main/java/com/jaymoskingdoms/GuardKit.java
  - kingdoms/src/main/java/com/jaymoskingdoms/Salvage.java
---
# Give guards ranks and paid-for armour

**Task:** Andy: one kingdom's guards all wore the same expensive suit; everything should have a real cost. Kit by rank and the settlement's stores and treasury, from his wardrobe pick list (https://claude.ai/artifact/4kM84VV7f7XqmjaQ9UpzHo).

## Plan
- Recruits wear only their pack (done), bearers salvage the dead's gear into stores, guards brag (done).
- GuardKit: Andy's table + notes as rules; pay spoils > stores > materials > treasury (keep 40); leather+mail floor; harness on everyone; daily review buys better.

## Notes
- Hooks: Folk.setup/restyle, McaEquipmentMixin (armour + blades), Ranks.sweep shields, HeraldryWear.sweep harness, Outfits.VERSION 4.

## Done
Built (compiles); not played.
