---
status: done
task: wither-reincarnated-fight
branch: claude/wither-reincarnated-fight
with: 
pr: 1717
mods: [kingdoms]
versions:
started: 2026-11-17
owns:
  - kingdoms/src/main/java/com/jaymoskingdoms/wither/
  - kingdoms/src/client/java/com/jaymoskingdoms/client/WitherNightClient.java
  - changes/kingdoms/wither-reincarnated-fight.md
---
# Wither: Reincarnated fight (behaviour half)

**Task:** Andy's nine behaviour picks for the Wither (night, ravage, beam, charge, skull rain, batted blue skulls, orbit, flee, dead rise). Model and sounds (wr_look) come later after previews.

## Plan
- New package `wither/`: WitherFight, WitherMoves, WitherSkulls, WitherNight.
- Small hooks: DarkNights, DeadEyes, Frights (public scare), JaymosKingdoms register.

## Notes
- No local builds (Andy's gaming PC): CI only.

## Done
Raising a Wither darkens the sky world-wide until it dies; it first throws skulls at nearby houses and walls (mobGriefing on), guards go for it, folk and animals run, nearby zombies, skeletons and draugr (never barrow) guard it. Then it circles the nearest player (tighter and faster in the last third) and uses beam, charge (shield stops and stuns it), skull rain on marked ground, and blue skulls a swing bats back. PR 1717, CI green.
Not tested in game at all: every number is a guess. Charge-block, bat and beam hit detection, steering via goal flags and noAi stun, and the ruin finder (block tags, not true settlement data) need a play. Model and sounds still to come.
