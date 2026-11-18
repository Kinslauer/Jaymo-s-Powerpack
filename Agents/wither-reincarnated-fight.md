---
status: running
task: wither-reincarnated-fight
branch: claude/wither-reincarnated-fight
with: 
pr: 
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
(pending)
