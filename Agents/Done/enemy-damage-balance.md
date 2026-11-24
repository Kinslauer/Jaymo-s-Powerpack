---
status: done
task: enemy-damage-balance
branch: claude/enemy-damage-balance
with: 
pr: 1970
mods: powers, kingdoms
versions: 
started: 2026-11-21
owns:
  - src/main/java/com/jaymospowers/power/zfighter/ZFighterRogue.java
  - src/main/java/com/jaymospowers/power/zfighter/ZFighterNpcKi.java
  - src/main/java/com/jaymospowers/power/xenoform/RivalAlien.java
  - src/main/java/com/jaymospowers/power/xenoform/XenoDouble.java
  - src/main/java/com/jaymospowers/power/chrono/ChronoEcho.java
  - src/main/java/com/jaymospowers/power/blindness/Unseeing.java
  - kingdoms/src/main/java/com/jaymoskingdoms/wild/Grizzly.java
  - kingdoms/src/main/java/com/jaymoskingdoms/wild/FeralWolf.java
  - kingdoms/src/main/java/com/jaymoskingdoms/wild/Coyote.java
  - kingdoms/src/main/java/com/jaymoskingdoms/wild/Moose.java
  - kingdoms/src/main/java/com/jaymoskingdoms/beasts/WildBoar.java
  - changes/powers/enemy-damage-balance.md
  - changes/kingdoms/enemy-damage-balance.md
---
# Enemy Damage Balance

**Task:** Reduce enemy damage across the board. Basic attacks should be the primary move and deal less per hit; heavy/damaging moves should be used only occasionally, not every hit. Applies to mobs/enemies in combat (Powers and/or Kingdoms combat systems).

## Plan
- Research how enemy attack damage and move selection works in Powers and Kingdoms
- Identify key classes controlling enemy combat behavior
- Implement damage reductions and move frequency adjustments
- Build and test changes

## Notes
- Combat balance task — likely touches Powers combat (zfighter, power systems) and/or Kingdoms mob combat
- Needs investigation into which classes control enemy attack damage and move selection
- No code changes yet — research first

## Done
- Reduced base damage and heavy move damage across all enemy types in Powers and Kingdoms
- Increased cooldowns on heavy moves (ki attacks, beast special moves) so they're used occasionally
- Basic melee attacks now deal low damage per hit, giving players time to react
- Created change notes for both Powers and Kingdoms
- PR #1970 opened for review
- Compiles but hasn't been played yet
- 2026-11-22 14:29 PR #1970 is merged into master; moved to Agents/Done.
