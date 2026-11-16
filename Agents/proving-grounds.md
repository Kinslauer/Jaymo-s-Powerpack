---
status: in-review
task: proving-grounds
branch: claude/proving-grounds
with:
pr: 1620
mods: kingdoms
versions:
started: 2026-11-15
owns:
  - kingdoms/src/main/java/com/jaymoskingdoms/oldage/ProvingGrounds.java
  - kingdoms/src/main/java/com/jaymoskingdoms/oldage/OldTemples.java
  - kingdoms/src/main/java/com/jaymoskingdoms/oldage/OldAge.java
  - kingdoms/src/main/java/com/jaymoskingdoms/PowersCompat.java (getUp only)
  - kingdoms/src/main/resources/data/jaymoskingdoms/dimension/proving_grounds.json
  - kingdoms/src/main/resources/data/jaymoskingdoms/dimension_type/proving_grounds.json
  - kingdoms/src/main/resources/data/jaymoskingdoms/worldgen/biome/proving_grounds.json
  - kingdoms/src/main/resources/assets/jaymoskingdoms/lang/parts/proving_grounds.json
  - changes/kingdoms/proving-grounds.md
---

# Proving Grounds (instanced boss arenas)

**Task:** Andy's Kageborne pick "Boss trials via altar offerings into instanced arenas". An offering (plus emeralds) on a lit old-age temple altar takes the offerer and their nearby party into a plot of their own in a void dimension, `jaymoskingdoms:proving_grounds`, to fight that patron's champion; back to the altar when it's won or lost.
