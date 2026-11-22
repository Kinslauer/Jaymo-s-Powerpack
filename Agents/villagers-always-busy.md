---
status: in-review
task: villagers-always-busy
branch: claude/villagers-always-busy-3238b4
with: Claude Code (PC, side session of the villages-plan session)
pr: 1981
mods: kingdoms
versions:
started: 2026-11-22
owns:
  - kingdoms/src/main/java/com/jaymoskingdoms/Routines.java
  - kingdoms/src/main/java/com/jaymoskingdoms/Spirits.java
  - kingdoms/src/main/java/com/jaymoskingdoms/Needs.java
  - kingdoms/src/main/java/com/jaymoskingdoms/Unstick.java
  - kingdoms/src/main/java/com/jaymoskingdoms/Meals.java (snack only)
  - kingdoms/src/main/java/com/jaymoskingdoms/TownHunger.java (one line)
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

- MCA's WORK also needs JOB_SITE and falls back to IDLE (javap of VillagerTasksMCA), so "no job site" alone isn't a stall.
- The Deck restart to run the log was blocked by my permission check; asked the parent session for Andy's OK.

## Done
PR #1981. Townsfolk get hunger/thirst/boredom (snack, drink, pastimes sooner), grumble when they go without, vanilla
tradesfolk with no job site get pastimes in work hours, and any villager still 2 min is logged `[stall]`, sent on at
3 min (home / job / bell, door, hop). Built on the Deck; not played; the stall's cause not yet read from a log.
