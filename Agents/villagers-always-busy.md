---
status: running
task: villagers-always-busy
branch: claude/villagers-always-busy-3238b4
with: Claude Code (PC, side session of the villages-plan session)
pr:
mods: kingdoms
versions:
started: 2026-11-22
owns:
  - kingdoms/src/main/java/com/jaymoskingdoms/Routines.java
  - kingdoms/src/main/java/com/jaymoskingdoms/Spirits.java
  - kingdoms/src/main/java/com/jaymoskingdoms/Needs.java
  - kingdoms/src/main/java/com/jaymoskingdoms/Unstick.java
---
# Villagers always doing something (villages plan, Track B)

**Task:** Andy, 2026-11-21: villagers must never stall for hours (seen standing still in houses, not at work or in bed);
they should always be doing something for a need (hunger, thirst, boredom). Find the stall in a log first, then a
watchdog and the needs.

## Plan
- Find the stall from the code (Routines, MCA's brain, our professions' job sites); log what's left
- Unstick: idle watchdog (logs at 2 min, acts at 3 min: home or job site, door, step, hop)
- Needs: hunger, thirst, boredom 0-100; highest picks the next thing; Spirits grumbles from it
- Build on CI, PR

## Notes
- Unstick.java already existed (path-headway unsticking, 0.5.36); the idle watchdog goes into it.
- Our own professions (Professions) have PoiType.NONE: no job site. Vanilla and MCA both need JOB_SITE for WORK, fall
  back to IDLE. Routines skips anyone with a trade in work hours (2000-9000), so jobless-site tradesfolk get only MCA idle.
- MCA's IDLE runs EnterFavoredBuildingTask first (priority 1): walks them into their favoured building and, once inside,
  does nothing; the strolls are the only thing that moves them on.

## Done
What a player will see, and what wasn't tested.
