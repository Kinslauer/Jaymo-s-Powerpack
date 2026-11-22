---
status: running
task: death-clip-bugs
branch: claude/sad-hodgkin-7d6dce
with: Claude (side session of local_2bfd1f34)
pr:
mods: powers, kingdoms, kitchen
versions:
started: 2026-11-22
owns:
  - kitchen/src/main/java/com/jaymoskitchen/corpse/Scavengers.java
  - kingdoms/src/main/java/com/jaymoskingdoms/RubberFolk.java
  - kingdoms/src/main/java/com/jaymoskingdoms/BioBuilt.java
  - kingdoms/src/main/java/com/jaymoskingdoms/PowersCompat.java (one new method)
  - src/main/java/com/jaymospowers/core/wounds/Mortality.java (one new predicate)
  - player-head rendering (client mixins, new)
---
# Death clip bugs (2026-11-22)

**Task:** Three bugs from Andy's death clip: zombies crowd his corpse in daylight instead of seeking cover; mob heads draw the
default player skin instead of the MCA look; a Rubber-folk in Limbo kept regrowing limbs.

## Plan
- Find each cause in code, fix, one PR with change notes per mod.

## Notes
- Clip (2026-11-22 16-45-20, 30 s): "You are in Limbo", and "A lost limb stretches back into place" shows twice. Burning
  zombies round his torn-up remains at dawn. Captain "Sir Aldous the Hollow" (skeleton) wears a player head.

## Done
