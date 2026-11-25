---
status: running
task: mordhau-1-helms
branch: claude/mordhau-1-helms
with: claude (PC worker, Opus 5.5)
pr: 
mods: kingdoms, powers
versions: 
started: 2026-11-24
owns:
  - kingdoms/tools/gen_heraldry_armour.py (new helm builds and the helm lists only)
  - kingdoms/src/main/resources/assets/jaymoskingdoms/bbmodels/heraldry_<new helm>.json and their textures, icons, item files
  - kingdoms/src/main/java/com/jaymoskingdoms/HeraldryArmour.java (helm registrations)
  - kingdoms/src/main/java/com/jaymoskingdoms/HeraldryWear.java, GuardKit.java (the realm kettle cut only)
  - src/main/java/com/jaymospowers/tinkering/smithing/HeraldryForging.java (new helms forgeable, painted barbutes)
  - changes/kingdoms/claude-mordhau-1-helms.md, changes/powers/claude-mordhau-1-helms.md
---
# Mordhau harness 1: helms

**Task:** Andy's 13 picked helm rows from the Mordhau pick list (Inbox `mordhau-1-helms`): our own models of the
historical helms each row is based on, made the way the harness makes its pieces; the kettle hat in a different cut
for different realms.

## Plan
- New helms in gen_heraldry_armour.py, registered in HeraldryArmour, forgeable at Powers' anvil.
- "have" rows: barbute and spangenhelm made forgeable; painted barbutes at the anvil.
- Kettle cuts by the realm's harness style (GuardKit/HeraldryWear).
- Previews to `Assets/mordhau-1-helms/`; lore (new) paragraphs for gallowglass, turban and spectacle helms.

## Notes
- 

## Done
What a player will see, and what wasn't tested.
