---
status: done
task: Downed enemies react to executions and stay down when killed
branch: claude/project-thread-dgrimh
with: 
pr: 1356
mods: powers, kitchen
versions: 
started: 2026-11-12
owns:
  - src/client/java/com/jaymospowers/client/wounds/ExecutionReact.java
  - src/main/java/com/jaymospowers/core/wounds/DownedLie.java
  - src/client/java/com/jaymospowers/client/wounds/DownedPose.java
  - src/main/java/com/jaymospowers/core/wounds/Downed.java (finish only)
  - kitchen/src/main/java/com/jaymoskitchen/corpse/Corpses.java (deathBody only)
---
# Downed execution reactions

**Task:** Andy: "downed enemies should react to the executions, i just watched jung wild slam a few zombies and they popped up after, instead of being beaten into the ground".

## Notes
- Cause: a downed mob keeps Pose.STANDING, so Kitchen's `Corpses.deathBody` began the corpse as a standing ragdoll that toppled.
- Fix: Powers tags the dying body `jaymoskitchen.lying.<u|d>.<yaw>` (`DownedLie.markBody` from `Downed.finish`), Kitchen starts the corpse lying from it.
- Reactions: `ExecutionReact` reads the synced `Finishers.FINISHING` / `WebExecutions.EXECUTING` on players and poses the body under it (brace, driven into the ground, pinned, strangle struggle).

## Done
Compile-checked, not played.
- 2026-11-12 06:10 PR #1356 is merged into master; moved to Agents/Done.
