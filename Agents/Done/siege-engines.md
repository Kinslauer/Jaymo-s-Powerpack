---
status: done
task: 
branch: claude/project-thread-beb6sd
with: 
pr: 1513
mods: kingdoms
versions: 
started: 2026-11-14
owns:
  - kingdoms/src/main/java/com/jaymoskingdoms/SiegeTrain.java
  - kingdoms/src/main/java/com/jaymoskingdoms/Haul.java
  - kingdoms/src/main/java/com/jaymoskingdoms/Mangonel.java
  - kingdoms/src/main/java/com/jaymoskingdoms/SiegeStone.java
  - kingdoms/src/client/java/com/jaymoskingdoms/client/MangonelRenderer.java
---
# Siege engines

**Task:** Andy linked Siegeworks: "can we have warring kingdoms bring siege engines?"

## Plan
- Own code and model (Siegeworks: code LGPL-3.0, art all rights reserved, 1.20.1/1.21.1 only).
- War parties bring a mangonel; rich realms a field cannon; pushed by two soldiers, worked at the siege.

## Notes
- Small edits in WarParties, Cannon, Cannonball, Cannons, CannonRenderer.
- Not built: ram, siege tower, ladders, bolt thrower (Siegeworks has them; offered to Andy).

## Done
War parties push a mangonel (or field cannon) to the siege and work it. Compiled, not played.
- 2026-11-14 08:35 PR #1513 is merged into master; moved to Agents/Done.
