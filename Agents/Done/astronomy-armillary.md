---
status: done
task: astronomy-armillary
branch: claude/astronomy-armillary
with: 
pr: 1196
mods: kingdoms
versions: 
started: 2026-11-09
owns:
  - kingdoms/src/client/java/com/jaymoskingdoms/client/asrt/ (armillary block entity renderer, screen)
  - kingdoms/src/main/java/com/jaymoskingdoms/ (armillary block, block entity, item, registration, net payload)
  - changes/kingdoms/claude-astronomy-armillary.md
---
# Astronomy 3: the armillary sphere

**Task:** An instrument that measures what's already discovered — position, location, world time — with animated mechanical feedback, and records the measurement. Depends on astronomy-sky (discovered stars); pairs with astronomy-telescope.

## Plan
- armillary sphere block + block entity + item, registered in Kingdoms
- block entity renderer: animated rings
- screen: readout of discovered stars overhead, world time, position; Record button persists the measurement
- net payload to open the screen from a right-click
- build kingdoms, pr_checks, change note, PR

## Notes
- Reuses astronomy-sky's SkyMath/Discovery on the client for the sky readout.
- "Records the measurement": block entity stores the last measurement (time, position, stars).

## Done
What a player will see, and what wasn't tested.
- 2026-11-09 13:16 PR #1196 is merged into master; moved to Agents/Done.
