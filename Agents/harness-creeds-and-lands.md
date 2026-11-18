---
status: in-review
task: harness-creeds-and-lands
branch: claude/harness-creeds-and-lands
with:
pr: 1728
mods: kingdoms
versions:
started: 2026-11-18
owns:
  - kingdoms/src/main/java/com/jaymoskingdoms/HeraldryArmour.java
  - kingdoms/src/main/java/com/jaymoskingdoms/HeraldryWear.java
  - kingdoms/src/main/java/com/jaymoskingdoms/Harness.java
  - kingdoms/tools/gen_heraldry_armour.py
---

# Harness 3: creeds and other lands

**Task:** Add creed-specific harness variants (faith-marked plate, oath sleeves, spiked black plate, bought harness, king's guard livery) and land-specific harness variants (fur-lined, mail of the sands, quilted cotton, hide and bronze, laced lamellar) for knights in those realms. Uses helms, finishes, coat of plates and tabard from tasks 1 and 2. Art drawn by gen_heraldry_armour.py.

## Plan
- Scan HeraldryArmour, HeraldryWear, Harness, HarnessMenu/HarnessScreen, HeraldryLayer, gen_heraldry_armour.py
- Add creed harness types (cr-faith, cr-chivalry, cr-conquest, cr-coin, cr-crown)
- Add land harness types (ld-snowy, ld-desert, ld-jungle, ld-savanna, ld-cherry)
- Update HeraldryWear for who wears and sells what (KNIGHT_LANDS expansion, BLACKENED_REALMS)
- Update gen_heraldry_armour.py to generate textures for new variants
- Build and test

## Notes
- Andy approved lore placement "The armourers' wider makes" as canon
- Must start after harness-body-pieces merges (uses helms, finishes, coat of plates, tabard from tasks 1 and 2)
- Five harness tasks touch HeraldryArmour and generator: take in order (helms, body, creeds/lands, barding, upkeep)

## Done
Added creed and land harness variants:
- **Creed variants**: Faith (etched cuirass), Chivalry (oath sleeves), Conquest (spiked blackened spaulders), Coin (etched cuirass, barbute helms), Crown (gilt-banded cuirass/spaulders, tabard)
- **Land variants**: Snowy (fur-lined full harness), Desert (quilted coat + veiled helm), Jungle (brine-soaked quilted cotton), Savanna (hide/bronze), Cherry (laced lamellar)
- 9 new finishes, 22 new pieces, expanded KNIGHT_LANDS to all 9 climates
- PR #1728 created, compiles and builds successfully, PR checks pass
- Not tested in game: knight spawning with correct variants, texture generation, King's Guard livery, oath sleeve loss