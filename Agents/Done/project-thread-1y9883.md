---
status: done
task: Klei_Wright's tundra, all 34 picks (docs/Tundra-Plan.md)
branch: claude/project-thread-1y9883
with: 
pr: 1358
mods: powers, kingdoms, kitchen
versions: 
started: 2026-11-12
owns:
  - docs/Tundra-Plan.md
  - src/main/java/com/jaymospowers/tundra/
  - src/client/java/com/jaymospowers/client/tundra/
  - tools/gen_tundra.py
  - kingdoms/src/main/java/com/jaymoskingdoms/wild/Muskox.java
  - kingdoms/src/main/java/com/jaymoskingdoms/wild/HerdPull.java
  - kingdoms/src/main/java/com/jaymoskingdoms/wild/FedPredators.java
  - tools/gen_muskox.py
  - docs/Muskox-Model-Spec.md
---
# Tundra

**Task:** Andy picked all 34 rows of the Tundra Drop picklist: build Klei_Wright's ecology tundra.

## Plan
- docs/Tundra-Plan.md: track A blocks and lichen (Powers), B muskox/herds/predators (Kingdoms), C worldgen, D Klei art pack + rule.

## Notes
- Cloud session; tracks A and B run as two agents in worktrees, merged into the one branch.

## Done
A player in new chunks finds the tundra and lichen woodland, living glow lichen, muskox herds and predators that get full. Change notes: changes/powers/tundra.md, tundra-world.md, changes/kingdoms/tundra.md, changes/kitchen/tundra.md. Compiles; nothing played; worldgen JSON not load-tested.
- 2026-11-12 07:01 PR #1358 is merged into master; moved to Agents/Done.
