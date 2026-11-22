---
status: running
task: survival-wounds-track1
branch: claude/cranky-booth-e665ec
with: survival picks track 3 (butchery) runs beside it
pr: 
mods: powers, kingdoms
versions: 
started: 2026-11-22
owns:
  - src/main/java/com/jaymospowers/core/wounds/Wounds.java
  - src/main/java/com/jaymospowers/core/wounds/BodyPlan.java
  - src/main/java/com/jaymospowers/core/wounds/HitRouting.java
  - src/main/java/com/jaymospowers/core/wounds/PartStrain.java (new)
  - src/main/java/com/jaymospowers/core/wounds/ (Treatment, Herbal, Bleeding, Downed, MedicineItems: small edits)
  - kingdoms/src/main/java/com/jaymoskingdoms/ClassPerks.java (ranger arrow tag only)
  - docs/Wounds-Plan.md
---
# Survival picks, track 1: Wounds

**Task:** Andy's LSO picks (docs/Survival-Picks-Plan.md track 1): own part pools on top of health, downing rule, 20%
debuffs, broken hearts, headshots, armour per part, medicine over time, food cost and sleep, painkiller check; the
Ranger's arrows go through the helmet.

## Plan
- Pools apart from health; head blow while standing kills, any other blacked vital downs.
- Headshot and armour per part in HitRouting; broken hearts as a max-health modifier.
- Medicine over time onto our kit; food cost; sleep heals every part.

## Notes
- Track 3 owns Kitchen corpse/butchery and Powers carrying (LimbUse/DroppedArms left alone).

## Done
