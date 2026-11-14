---
status: done
task: 
branch: claude/project-thread-76m9kc
with: Claude (cloud thread "Add sparks to blade clashes and parries")
pr: 1288, 1289 (both merged)
mods: powers, kingdoms
versions: 
started: 2026-11-11
owns:
  - src/main/java/com/jaymospowers/combat/ClashFx.java
  - src/main/java/com/jaymospowers/combat/ShieldShatter.java
  - src/client/java/com/jaymospowers/client/combat/ClashFxClient.java
  - src/main/java/com/jaymospowers/combat/Guard.java (the parry's particles only)
  - kingdoms/src/main/java/com/jaymoskingdoms/ShieldBearers.java
  - kingdoms/src/main/java/com/jaymoskingdoms/BladeClash.java, MobParry.java, MobShields.java (particles, shield odds)
---
# Clash sparks, splintering shields, more shield bearers

**Task:** Andy (2026-11-11, film notes): "when swords clash or when parries occur there should be particle sparks", "splintering
wood from shattering shields", "some of the fighters should have shields so the fight looks realistic", all in real gameplay.

## Plan
- Powers `combat/ClashFx`: our own spark particle (hot, falls, bounces) and wood splinter particle; one call each for the film thread.
- Sparks on every parry (Guard, Darktide blade, Kingdoms MobParry), blade clash (Kingdoms BladeClash) and metal-on-metal block.
- Wooden shields chip on each blow and shatter (a mob's after enough blows, anyone's when the item breaks).
- Kingdoms: barbarians, orc grunts, goblins, tiered undead carry shields more often and raise them more.

## Notes
- The film thread (PR #1284, "Move every model onto GeckoLib animations") reuses the effects.

## Done
- 2026-11-11 15:06 No sign of work for 30 min: marked stalled (whoever ran it stopped).
- 2026-11-12 05:20 PR #1288 is merged into master; moved to Agents/Done.
