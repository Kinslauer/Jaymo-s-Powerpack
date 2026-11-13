---
status: done
task: Get the Deck server stable (Andy, 2026-11-13 10:10 AM): check the deploy, watch the log, join with the owed village start, fix crashes, stop after 30 min clean or 2 rounds with no progress
branch: deck-start-waits-for-land, start-wait-one-minute
with: local desktop session (PC)
pr: "#1430, #1431, #1435, #1451, #1454, #1457, #1458, #1459 (merged)"
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

- Review (3 lenses + verifiers): outside a start the no-make rule broke the whaler shore, trader second town, citadel,
  captors, shelter, so it applies only while a start is tried (old budget elsewhere). Monolith reuses primed land.
  PR #1430 merged on green CI ~10:50 AM; keep-world deploy started.

- 10:48 AM deploy (Kingdoms 0.8.7) up. Andy joined ~11:04: no crash; the village start waited 4 times, 2-3 min each
  (~9 min in all), then landed. Andy: "taking far too long, maybe after a minute it should just give the fallback".
- #1431 (merged ~11:20 AM): LAND_WAIT_MS = 60 s real time across all waits, then it makes do. Redeploying.

- 11:36 AM deploy (Kingdoms 0.8.8) up. Andy rejoined as Niels 11:53 AM: "it seems to be working for now".
  Watching till 12:23 PM (30 min clean).
- 11:57 AM crash (round 2): Powers SkyfallCleanup sweep read a chunk that `hasChunk` said was there but was still being
  made (26.3 hasChunk = holder at FULL ticket level, not made). #1435 (merged ~12:45 PM): 23 server-side hasChunk guards
  -> getChunkSource().getChunkNow; Seats.java:133 kept (one-shot kingdom founding would found a castle).
- 12:14 PM deploy up (built from #1435's merge commit, so the jar names lag: Powers 0.23.11, Kingdoms 0.8.10). Niels =
  Jungxxv (friend); Andy = Manigraphic78 / Hendrikus, on from 12:30:52 PM. Stable: no crash or freeze from 12:14 to
  past 1:04 PM, 30+ min with Andy logged in. Task done.
- 1:51 PM and 1:58 PM crashes: `/kingdom mohknee go` made the landing chunk [1750,0] on the server thread. Andy chose
  "go waits behind loading": #1451 (merged ~2:30 PM) adds StoryGround.travel (background PORTAL ticket, loading screen,
  2-min give-up); go refused while held in story stasis.
- 2:23 PM crash: Sightings.rumour read a far spot's biome (level.getBiome waited on chunk [20,-9]). #1454: biome
  source when unloaded.
- Audit (8 scanners over 323 tick files + verifiers): 9 more far reads. #1457 Kingdoms (Roads stoneNear/openEnds,
  Elders/Couriers putBack, Mines catch-up, Journeys arrive, Milestones), #1458 Powers (Karma descent, WindAether
  topAt, ZFighter raiders/yard/surface). Merged ~3:50 PM, redeploying. WindAether still makes its one arrival chunk.
- 3:36 PM crash: Traps.pits read the ground beside Andy just set down on Mohknee (fresh land), via Powers'
  Stacked.surface getHeight hook, chunk [1748,1] still generating. Root cause: 26.3 Level.isLoaded == hasChunk (true for
  scheduled chunks). #1459: Loaded.made(level,pos) per mod (getChunkNow on ServerLevel, unchanged elsewhere), 359 sites
  swapped; 24 sites verified harmful (data lost / one-shot) kept on isLoaded; Loaded.top for 8 read-before-check sites.
  Merged ~5:05 PM, redeploying. Follow-up: the 24 kept sites still block on generating chunks; fix each with
  skip-and-retry (fixes listed in the verify workflow wf_c631bde4-f56).

## Still to do
- Review follow-ups (start path still makes chunks): BURNED burningVillage scan (72-block box), SKYFALL crater
  neighbours, SENTENCED seat; out-of-start budgets (shore 12, ground 6) should move to background jobs too.
- From the stall audit, not fixed: FutureStart.restore placeInWorld on every leap into an empty future;
  ZFighterAfterlife first build at x=20000; ZFighterSites tower/Lookout one-time builds; many other `level.getChunk`
  calls in Kingdoms (Couriers, Elders, Emigrants, Envoys, Roads, Speakeasy, Milestones...).
- FarmhandHome farmhouse hunt is off (SEARCH_OFF): rebuild it on Scout + ahead.
- 2026-11-13 11:38 PR #1430 is merged into master; moved to Agents/Done.
