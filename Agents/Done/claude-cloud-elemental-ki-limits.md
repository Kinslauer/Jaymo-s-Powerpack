---
status: done
task: elemental-ki-limits
branch: claude/project-thread-dnj6tx
with: Claude (cloud project thread)
pr: 1333
mods: powers, kingdoms
versions:
started: 2026-11-11
owns:
  - src/main/java/com/jaymospowers/power/zfighter/ZFighterScouter.java
  - kingdoms/src/main/java/com/jaymoskingdoms/Mana.java
  - kingdoms/src/client/java/com/jaymoskingdoms/client/ManaClient.java
  - kingdoms/src/client/java/com/jaymoskingdoms/client/spirits/ManaWings.java
  - kingdoms/src/client/java/com/jaymoskingdoms/client/spirits/SpiritArms.java
  - kingdoms/src/client/java/com/jaymoskingdoms/client/spirits/SpiritHandRenderer.java
  - kingdoms/src/client/java/com/jaymoskingdoms/client/spirits/ClashCinematic.java
---
# Elemental powers: weak auras, blind to mana

**Task:** Andy: Smoke, Gravity and Cryo users have the weakest ki auras and can't see mana abilities or spirits; they can still develop valor.

## Plan
- Powers: scouter power level x0.4 for elemental holders (ki sense reads them weaker).
- Kingdoms: Mana.blindToMana/seesMana gate every "who sees mana" check on the client.

## Done
- PR #1333. Elemental users: power level x0.4 (ki sense reads them weaker); never see others' mana glow, wings, spirits, mage hands or clash bars. Valor unchanged.
- Built; not played. Lore.md has a (new) paragraph for Andy.

## Notes
- 2026-11-11 22:21 PR #1333 is merged into master; moved to Agents/Done.
