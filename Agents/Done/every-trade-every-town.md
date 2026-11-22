---
status: done
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
PR #1980. Every settlement keeps butcher, healer (new trade), cook, cleric, farmer, tanner, fisher by water; miner and
woodcutter once a mine and tree farm exist. Epic towns are refitted the first time a player walks in: our butcher block
and hooks, stoves, temple shrine, infirmary cot and mortar, soaking cauldron and drying rack, trough, chopping block;
halls at the edge for trades with no house. Built on the Deck (7b7d4d3) and CI. Not run in game: the Deck server is the
live world. Step 5 still to do after the merge (walk into a town per land, read the "Fitted the town" log line).
- 2026-11-22 03:22 PR #1980 is merged into master; moved to Agents/Done.
