---
status: running
task: villages plan, Track C
branch: claude/every-trade-every-town-b12729
with: Claude Code (Opus 5.5)
pr: 1980
mods: kingdoms
versions: 
started: 2026-11-22
owns:
  - kingdoms/src/main/java/com/jaymoskingdoms/JobHalls.java
  - kingdoms/src/main/java/com/jaymoskingdoms/Professions.java
  - kingdoms/src/main/java/com/jaymoskingdoms/TownFitting.java
  - kingdoms/src/main/java/com/jaymoskingdoms/TownTrades.java
---
# Every trade in every town

**Task:** Andy, 2026-11-21: every town's buildings hold our new blocks for its trades, and every land has a butcher,
healer, cook, cleric, miner, woodcutter, farmer and tanner (fisher where there's water). Track C of the villages plan.

## Plan
- Roster: promote missing trades from the jobless (Professions).
- TownFitting: after an Epic town is placed, swap vanilla job blocks for ours; trades with no house get a JobHalls hall.
- Old towns: refit once when a player first comes near.

## Notes
- Not touching EpicTowns, Routines, Spirits, StoryGround, VpBuildings (Tracks A/B, PR #1973).
- Roster extends TownTrades (it already kept butcher + cleric) instead of a second roster. Healer is a new profession.
- One fitter for new, raised and old towns: scans players' chunks for village_* starts, marks them in the Overworld's
  towns_fitted attachment. /place structure doesn't register starts (PlaceCommand), so it can't be tested that way.
- Deck server is the live world: no in-game test there without Andy's OK (refits are permanent).

## Done
