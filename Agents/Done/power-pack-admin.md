---
status: done
task: Live-monitor the Deck server launch and fix what breaks until it's stable (Andy, 2026-11-13 07:31 / 07:45)
branch: (several, all merged)
with: cloud session "Power pack admin session"
pr: "#1425, #1427, #1428, #1429 (merged)"
mods: Powers, Kingdoms, Dynamic Waters port
versions: Powers 0.23.11, Kingdoms 0.8.6 (deployed by the 07:59 pbdr, not yet confirmed up)
started: 2026-11-13
owns:
  - (nothing open)
---
# Power pack admin: Deck server stability

**Task:** Andy: "take control and live monitor the server launch ... keep going until it is stable". He allowed merging fixes on green CI, launching the CurseForge profile, testing joins, and closing the game. Longer loading screens are fine ("players have to read the hints").

## Notes
- Every Deck crash on 2026-11-12/13 was the server thread making chunks while it waited (watchdog at 60 s). Right after a join the worldgen queue is long, so even one synchronous chunk can wait past the watchdog. Rule: never make chunks on the server thread; predict (Scout) or load in the background (tickets) and wait over ticks.
- Scout (kingdoms Scout.java, powers core/Scout.java): structure finder that never loads chunks (placement grid, surface biome check, StructureManager.checkStructurePresence, CHUNK_LOAD_NEEDED taken as found).
- StoryGround.ahead: a village/shipwreck start's 3x3 chunks loaded in the background (PORTAL ticket) before the start runs; player held on the loading screen up to 2 min.
- PC helper (local, not in git): "Watch Deck Server.bat" + deck\watch-server.ps1 stream the Deck's logs/latest.log into modpack\deck-live\server.log (reconnects by itself). Get Deck Logs.bat for crash reports.
- 07:59 pbdr (keep world) started by Claude via Explorer; result not confirmed (PC link dropped at ~08:50).

## Still to do
- Confirm the 07:59 deploy finished; watch Andy's rejoin (Hendrikus's village start is still owed: it exercises Scout + ahead).
- Confirmed by the stall audit but not fixed: FutureStart.restore placeInWorld on every leap into an empty future; ZFighterAfterlife first build at x=20000; ZFighterSites tower/Lookout one-time builds; LifeStory.reach still makes up to 6 chunks synchronously for starts without an ahead job.
- FarmhandHome farmhouse hunt is off (SEARCH_OFF): rebuild it on Scout + ahead.
- 2026-11-13 09:47 PR #1425 is merged into master; moved to Agents/Done.
