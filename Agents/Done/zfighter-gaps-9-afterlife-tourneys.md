---
status: done
task: zfighter-gaps-9-afterlife-tourneys
branch: claude/zfighter-gaps-9-afterlife-tourneys
with: 
pr: 1956
mods: powers
versions: Powers 0.26.22
started: 2026-11-20
owns:
  - src/main/java/com/jaymospowers/power/zfighter/PackRules.java (new)
  - src/main/java/com/jaymospowers/power/zfighter/ZFighterAfterlife.java
  - src/main/java/com/jaymospowers/power/zfighter/ZFighterArts.java
  - src/main/java/com/jaymospowers/power/zfighter/ZFighterCreator.java
  - src/main/java/com/jaymospowers/power/zfighter/ZFighterDrillBeam.java
  - src/main/java/com/jaymospowers/power/zfighter/ZFighterKi.java
  - src/main/java/com/jaymospowers/power/zfighter/ZFighterTournaments.java
  - src/main/java/com/jaymospowers/power/zfighter/ZFighterWishes.java
  - src/main/resources/assets/jaymospowers/lang/parts/zfighter.json
  - src/main/resources/assets/jaymospowers/lang/parts/zfighter_afterlife.json
  - changes/powers/zfighter-gaps-9-afterlife-tourneys.md
---
# Z Fighter gaps 9: borrowed time, spared masters' homes, themed tournaments

**Task:** Borrowed time from the dead (fortune teller lends minutes among the living; forms burn it faster; only a wish makes it lasting), masters' homes spared from ki block-breaking, and themed tournaments (a tyrant's games, the fortune teller's five fighters) beside the kingdom tournaments, last-one-standing and the dead's tournament — Z Fighter gap checklist picks for Jaymo's Powers (no new mobs, per Andy).

## Plan
- BORROWED attachment on ZFighterAfterlife: fortune teller lends 12000 ticks for 24 silver or 400 TP; per-second burn, twice as fast in a form; message when it is up
- New LASTING wish (index 13) on ZFighterWishes makes borrowed time stop burning
- New PackRules: mob griefing rule, masters' homes (kingdom/barbarian seats, 24 blocks) and kept tournament rings spared from ki; gate drill beams, ki balls, waves, ape beam on it
- ZFighterTournaments: 25% themed openings, tyrant champion entry with double prize, five-fighter entry cap
- Lang keys in zfighter.json / zfighter_afterlife.json; change note changes/powers/zfighter-gaps-9-afterlife-tourneys.md

## Notes
- No new original mobs; no edits to ZFighterPower.java (owned by open zfighter-aura-rework); no slash commands or discovery chat text
- Not tested in game: sh ./gradlew build passes, tools/pr_checks.py passes
- PR: https://github.com/Kinslauer/Jaymo-s-Powerpack/pull/1956

## Done
What a player will see, and what wasn't tested.
- 2026-11-22 10:08 PR #1956 is merged into master; moved to Agents/Done.
