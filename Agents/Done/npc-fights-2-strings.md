---
status: merged
task: NPC fights patch 2, strings, chases and ki
branch: claude/npc-fights-2-strings
with: 
pr: 1836
mods: powers
versions: 
started: 2026-11-19
owns:
  - src/main/java/com/jaymospowers/power/zfighter/ZFighterStrings.java
  - src/main/java/com/jaymospowers/power/zfighter/ZFighterNpcKi.java
  - src/main/java/com/jaymospowers/power/zfighter/ZFighterRogue.java (strings moved out)
---
# NPC fights 2: strings, chases and ki

**Task:** F4, F5, F7 for tyrants, Patrolmen and Z Fighter gifted folk, against players too.

## Done
PR #1836, CI green. Not played. NPC beam entry: `ZFighterNpcKi.fireBeam`, hook `ZFighterNpcKi.taker`.
