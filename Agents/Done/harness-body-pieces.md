---
status: done
task: harness-body-pieces
branch: claude/harness-body-pieces
with: Claude Code (PC)
pr: 1868
mods: kingdoms
versions: 
started: 2026-11-19
owns:
  - kingdoms/src/main/java/com/jaymoskingdoms/HeraldryArmour.java
  - kingdoms/src/main/java/com/jaymoskingdoms/HeraldryWear.java
  - kingdoms/src/main/java/com/jaymoskingdoms/Harness.java
  - kingdoms/src/main/java/com/jaymoskingdoms/HarnessMenu.java
  - kingdoms/src/client/java/com/jaymoskingdoms/client/HeraldryLayer.java
  - kingdoms/tools/gen_heraldry_armour.py
---
# Harness 2: new body pieces and two new places

**Task:** Andy ticked the Harness Pattern Book's body rows (fluted and rounded cuirass, coat of plates, gorget with a NECK place, faulds and tassets with a WAIST place, rondels, heraldic tabard); "start on harnesses" (2026-11-19).

## Plan
- Read the harness code; add the pieces, finishes and places; art by gen_heraldry_armour.py; who sells/wears them; lang; change note.
- Build on the Deck or CI.

## Notes

## Done
PR #1868. Knights wear their realm's cuirass make (plain, fluted or rounded), a gorget, faulds and tassets, and some realms add rondels. Poorer realms' guards wear the coat of plates, which realm armourers sell. Tabards show the realm's arms, and realm tailors sell them. Two new harness places, Neck and Waist. Also fixed the art generator, so Harness 3's creed and land pieces finally have models. Built on the Deck and PR checks pass; not played.
