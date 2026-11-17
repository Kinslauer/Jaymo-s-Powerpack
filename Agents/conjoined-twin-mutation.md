---
status: running
task: conjoined-twin-mutation
branch: claude/conjoined-twin-mutation
with:
pr:
mods: powers
versions:
started: 2026-11-16
owns:
  - src/main/java/com/jaymospowers/mutation/ConjoinedTwins.java
  - src/main/java/com/jaymospowers/mixin/ConjoinedTwinVillagerMixin.java
  - src/main/java/com/jaymospowers/mixin/ConjoinedTwinZombieMixin.java
---
# Conjoined Twin Cosmetic Mutation

**Task:** Research and implement a cosmetic mutation where villagers and zombies spawn as conjoined twins — a baby/toddler mounted on an adult's shoulder. Cosmetic only, no gameplay impact.

## Plan
1. Research existing entity mounting/passenger systems in the codebase (Younglings, Perch, NaturalSpawnerMixin)
2. Create ConjoinedTwins class (similar to Younglings) to handle spawning logic
3. Create entity type tag for entities that can have conjoined twins
4. Create mixins for Villager and Zombie to override positionRider for shoulder positioning
5. Hook into entity spawning to apply the mutation
6. Test compilation

## Notes
- Reference: Video showing baby villager on adult's shoulder (conjoined twins aesthetic)
- Target entities: Villagers, Zombies, MCA villagers (toddler MCA on adult MCA shoulder)
- Key patterns found:
  - Younglings.spawned(): hooks into mob finalizeSpawn, creates young entities via addFreshEntityWithPassengers
  - Perch system: uses PerchRidingMixin on Entity to override positionRider for shoulder/head/draped positioning
  - NaturalSpawnerMixin: wraps addFreshEntityWithPassengers to replace/modify spawned entities
- Approach: Create a mixin on Villager and Zombie that overrides positionRider to position conjoined twin passengers on the shoulder (similar to Perch.ridingPosition but for adult villagers/zombies)
- Use an attachment to mark entities that have a conjoined twin passenger
- Spawning hook: can use ServerEntityEvents.ENTITY_SPAWN or mixin into NaturalSpawner like the loot goblins

## Done
Research complete. Implementation plan documented. No code changes yet.