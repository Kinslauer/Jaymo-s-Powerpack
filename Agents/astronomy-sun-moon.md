---
status: running
task: astronomy-sun-moon
branch: claude/astronomy-sun-moon
with: 
pr: 
mods: kingdoms
versions: 0.7.676
started: 2026-11-09
owns:
  - kingdoms/src/main/java/com/jaymoskingdoms/astronomy/SunMoon.java
  - kingdoms/src/main/java/com/jaymoskingdoms/astronomy/Observation.java
  - kingdoms/src/client/java/com/jaymoskingdoms/client/astronomy/SunMoonClient.java
  - kingdoms/src/main/resources/assets/jaymoskingdoms/lang/parts/astronomy.json
  - kingdoms/gradle.properties (version 0.7.676)
  - kingdoms/MODLOG.md (one entry)
  - changes/kingdoms/claude-astronomy-sun-moon.md
  - docs/codemap/kingdoms.md (regenerated)
---

# Astronomy 5: sun, moon and eight phases

**Task:** A simple orbital model, eight lunar phases, a Sun and Moon you can observe and record, and simulated blindness from clicking the Sun before the Optics skill.

## Plan
- `SunMoon` orbital model: sun/moon direction from world time, the 8 lunar phases (MoonPhase), phase names
- `Observation` server attachment (observed phases + sun/moon, synced to the player's client); client reports a look at the sun/moon held long enough
- `SunMoonClient`: track sun/moon position each tick, look duration, observation payload; left-click on the sun sends a glare payload; the server applies brief vanilla blindness unless Optics (placeholder returns false while the Optics skill doesn't exist)
- lang part `astronomy.json`: phase names and messages
- Build kingdoms, pr_checks, MODLOG + change note + codemap, PR

## Notes
- Depends on astronomy-sky (PR #1193, open, unmerged) for the observation rules and on astronomy-scholar-journal for where records go: this branch builds self-contained on master so it compiles, and follows the DiscoveryAttachment pattern so it can merge with them later.
- Blindness reuses the existing darkness feel (vanilla Blindness effect = darkening, can't read the sky) rather than a new full-screen effect.
- Not tested in game.

## Done
What a player will see, and what wasn't tested.
