---
status: in-review
task: vampire-leap-holy
branch: claude/vampire-leap-holy
with:
pr: 1680
mods: [kingdoms]
versions:
started: 2026-11-17
owns:
  - kingdoms/src/main/java/com/jaymoskingdoms/BloodLeap.java
  - kingdoms/src/main/java/com/jaymoskingdoms/HolyDread.java
  - kingdoms/src/client/java/com/jaymoskingdoms/client/mixin/vampires/BloodLeapUseMixin.java
  - kingdoms/src/main/resources/assets/jaymoskingdoms/lang/parts/vampire-holy.json
---
# Vampire right-click leap and holy dread

**Task:** Andy (clip of a vampire trying to reach prey on a tower): right-click locked on leaps to the mob and feeds; not locked, a strong directional jump; all weaker in sunlight. Vampires take the patrons' negative effects near holy items and patron interactions.

## Done
Right-click with an empty hand: locked on, a leap at the target (18 blocks) that feeds on arrival; else a strong jump. Weaker in sun or near holiness. Holy dread from held symbols/patron weapons, clerics/paladins/priests, shrines/altars, temples: Weakness, smoke, burning up close, wrath ladder if they linger; swearing/prayer/offering/boon burns. Small edits to BloodGrab (feedOn), NightGifts (thrown/push), PatronWrath (omen/bolt/smite visibility), Patrons, Blessings, PowersCompat (lockedClient), JaymosKingdoms. Built by CI only; not played.
