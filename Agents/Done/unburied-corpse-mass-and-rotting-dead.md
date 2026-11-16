---
status: done
task: Unburied stood whole by day with no corpses; the long dead come apart
branch: claude/project-thread-gy0d2v
with: Claude (cloud thread "Fix the Unburied's corpse mass")
pr: 1470
mods: Kingdoms, Kitchen, Powers
versions: 
started: 2026-11-13
owns:
  - kingdoms/src/main/java/com/jaymoskingdoms/blossomland/Unburied.java
  - kitchen/src/main/java/com/jaymoskitchen/corpse/CorpseEntity.java
---
# Unburied corpse mass and rotting dead

**Task:** Andy: "the unburied was here alive and well, and had no conglomeration of corpses like it should", then: it should not
be liftable, lifting the undead should tear off a limb at most, skeletons fall to pieces on death, hits to corpses dismember.

## Notes
- The Unburied's coming-apart ran in `customServerAiStep`, which stops once `isImmobile` is true: it froze whole at dawn. Moved to `tick`.
- Egg-raised ones take nearby dead and make up the rest; the Unburied is in Kitchen's `no_corpse`.
- Kitchen: `comesApart`, `losePart`, `fallToPieces`, `knockApart`, `setOnPart` hook; Powers makes the parts `SeveredPart`s.

## Done
Built; not played.
- 2026-11-13 18:09 PR #1470 is merged into master; moved to Agents/Done.
