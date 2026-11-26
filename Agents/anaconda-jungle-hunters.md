---
status: stalled
task: Fix the anaconda model and make anacondas jungle ambush hunters
branch: claude/project-thread-k10pz4
with: Claude (Reason Art project thread)
pr: 
mods: kingdoms
versions: 
started: 2026-11-26
owns:
  - kingdoms/src/main/java/com/jaymoskingdoms/wild/Anacondas.java
  - kingdoms/src/main/java/com/jaymoskingdoms/mixin/alexsmobs/
  - kingdoms/src/main/resources/jaymoskingdoms.alexsmobs.mixins.json
---
# Anacondas: jungle ambush hunters

**Task:** Andy (2026-11-25, clip): "the anaconda model is bugged, and i think they should spawn in the jungle no? hunting the other animals and players from the treees and rivers". Alex's Mobs Continued's anaconda.

## Plan
- Spawns: AM's own homes are swamps + a few Terralith biomes (its forge:is_swamp tag never matches on Fabric). Moved to jungles, mangroves, rivers with a jungle within 32 blocks.
- Ambush: lies still in water or on a leafy branch, climbs a tree unseen, drops/surges onto prey, no fall damage; sated_predators.
- Model: waiting on Andy to say what looks wrong.

## Notes
- Parallel crocodile thread: PR #2200 (DeathRoll), kept separate.

## Done
Compile-checked only, not played.
- 2026-11-26 00:07 No sign of work for 30 min: marked stalled (whoever ran it stopped).
