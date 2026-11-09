---
status: running
task: astronomy-sky
branch: claude/astronomy-sky
with: 
pr: 1193
mods: kingdoms
versions: 
started: 2026-11-09
owns:
  - kingdoms/src/client/java/com/jaymoskingdoms/client/astronomy/
  - kingdoms/src/client/java/com/jaymoskingdoms/client/JaymosKingdomsClient.java
  - kingdoms/src/main/resources/assets/jaymoskingdoms/astronomy/
  - kingdoms/src/main/resources/assets/jaymoskingdoms/textures/astronomy/white.png
  - kingdoms/src/main/resources/licenses/astronomy/
  - changes/kingdoms/claude-astronomy-sky.md
---
# Astronomy 1: a real night sky

**Task:** A real night sky over the overworld: HYG catalog stars to magnitude 6.5, 88 constellation figures that
connect as stars are discovered by looking, position/time-dependent sky, light pollution near settlements.

## Plan
- data files: stars.json (8,870 stars), constellations.json (88 figures, 726 segments), white.png
- client package com.jaymoskingdoms.client.astronomy (catalog, constellations, discovery, sky math, renderer)
- register SkyClient in JaymosKingdomsClient
- build kingdoms, pr_checks, change note, PR

## Notes
- Coexistence: additive world geometry via LevelRenderEvents.COLLECT_SUBMITS (the HighSkyClient pattern); the vanilla sky renderer is not touched, so Iris/Sodium keep their own sky.
- Discovery: per-player JSON in the client config dir; looking at a star for ~4 s discovers it; constellation lines draw between discovered stars.

## Done
What a player will see, and what wasn't tested.
