---
status: in-review
task: harness-helms-finishes-crests
branch: claude/harness-helms-finishes-crests
with: 
pr: 1721
mods: kingdoms
versions: 
started: 2026-11-18
owns:
  - kingdoms/src/main/java/com/jaymoskingdoms/HeraldryArmour.java
  - kingdoms/src/main/java/com/jaymoskingdoms/HeraldryWear.java
  - kingdoms/tools/gen_heraldry_armour.py
  - kingdoms/src/main/resources/assets/jaymoskingdoms/lang/parts/heraldry-armour.json
  - kingdoms/src/main/resources/assets/jaymoskingdoms/lang/parts/heraldry-armour-items.json
---

# Harness 1: new helms, finishes and crests

**Task:** Add 6 new helms, 5 new finishes, and new crest beasts/plumes to the heraldry armour system.

## Plan
1. Add new helms to HeraldryArmour.java: hm-great, hm-barbute, hm-burgonet, hm-spangen, hm-lobster, hm-grotesque
2. Add new finishes to HeraldryArmour.java and gen_heraldry_armour.py: fn-russet, fn-blued, fn-painted, fn-etched, fn-rusted
3. Add new crest beasts to gen_heraldry_armour.py: cs-beasts (griffin, bear, wolf, raven, hound), cs-plume
4. Update HeraldryWear.java to use new helms and finishes appropriately
5. Run generator to create models/textures
6. Update lang files
7. Build and test

## Notes
- Helms need Face type (OPEN, VISORED, FIXED), sight value, crestRise value
- Finishes need palette colors in FINISHES dict in generator
- Crests need new build functions in generator
- Generator creates models, textures, icons, item models, lang entries

## Done