---
status: running
task: Get the Deck server stable (Andy, 2026-11-13 10:10 AM): check the deploy, watch the log, join with the owed village start, fix crashes, stop after 30 min clean or 2 rounds with no progress
branch: deck-start-waits-for-land
with: local desktop session (PC)
pr: (opening)
mods: Kingdoms
versions: Deck runs Powers 0.23.11, Kingdoms 0.8.6 (the 08:08 AM deploy came up fine)
started: 2026-11-13
owns:
  - kingdoms/src/main/java/com/jaymoskingdoms/LifeStory.java
  - kingdoms/src/main/java/com/jaymoskingdoms/StoryGround.java
  - changes/kingdoms/deck-start-waits-for-land.md
---
# Power pack admin: Deck server stability

**Task:** Andy: get the Deck server stable, then stop. Merge fixes on green CI, launch the CurseForge profile, join,
let the owed village start run, close the game when needed. Long loading screens are fine.

## Notes
- Every Deck crash on 2026-11-12/13 was the server thread making chunks while it waited (watchdog at 60 s). Rule: never
  make chunks on the server thread; predict (Scout) or load in the background (tickets) and wait over ticks.
- Scout (kingdoms Scout.java, powers core/Scout.java): structure finder that never loads chunks.
- StoryGround.ahead: a village/shipwreck start's 3x3 chunks loaded in the background before the start runs.
- PC helpers (local, not in git): "Watch Deck Server.bat" + deck\watch-server.ps1; "Get Deck Logs.bat". SSH key is
  `~/.ssh/jaymos_deck` (deck@10.0.0.164).

## Log
- 10:12 AM: the 08:08 deploy (Powers 0.23.11, Kingdoms 0.8.6) was up; Hendrikus joined 10:10:56 and the server hit the
  watchdog at 10:12 (crash-2026-11-13_10.12.00). Stack: road packet -> depart -> (village landAhead found nothing, so
  no ahead job) -> begin -> village found no ground -> wild fallback spot() -> ground() -> reach() -> level.getChunk.
- Fix on `deck-start-waits-for-land`: reach() never makes chunks; a start notes the land it wanted (LifeStory.Wanted),
  StoryGround.ahead(player, chunks) makes it in the background, and the start tries again (3 tries for the chosen
  start, 5 in all). Kingdoms builds.

## Still to do
- Review, PR, merge on green CI, deploy, rejoin and watch 30 min.
- From the stall audit, not fixed: FutureStart.restore placeInWorld on every leap into an empty future;
  ZFighterAfterlife first build at x=20000; ZFighterSites tower/Lookout one-time builds; many other `level.getChunk`
  calls in Kingdoms (Couriers, Elders, Emigrants, Envoys, Roads, Speakeasy, Milestones...).
- FarmhandHome farmhouse hunt is off (SEARCH_OFF): rebuild it on Scout + ahead.
