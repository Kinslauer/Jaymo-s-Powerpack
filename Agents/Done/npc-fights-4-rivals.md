---
status: merged
task: NPC fights patch 4 (F14 sworn foes, F15 showdowns)
branch: claude/npc-fights-4-rivals
with: npc-fights-1-brawler (merged #1825)
pr: 1829
mods: powers
versions: 
started: 2026-11-19
owns:
  - src/main/java/com/jaymospowers/power/zfighter/ZFighterPatrol.java (foe, showdown)
  - src/main/java/com/jaymospowers/power/zfighter/ZFighterRogue.java (registerGoals)
  - src/main/java/com/jaymospowers/power/zfighter/ZFighterRaiders.java
  - src/main/java/com/jaymospowers/power/zfighter/ZFighterRaids.java
  - src/main/java/com/jaymospowers/power/zfighter/ZFighterInvasions.java (hook)
  - src/main/java/com/jaymospowers/power/zfighter/ZFighterKingdoms.java (report reflection)
---
# NPC fights 4: tyrants vs the Patrol

**Task:** F14 sworn foes, F15 showdowns; players can join either side.

## Notes
- Flight via Brawl.flyTo (patch 1, #1825).

Done: PR #1829, CI green, not played.
