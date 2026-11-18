---
status: done
task: Get the Deck server stable (Andy, 2026-11-13 10:10 AM): check the deploy, watch the log, join with the owed village start, fix crashes, stop after 30 min clean or 2 rounds with no progress
branch: deck-start-waits-for-land, start-wait-one-minute
with: local desktop session (PC)
pr: "#1430, #1431, #1435, #1451, #1454, #1457, #1458, #1459, #1466, #1471, #1472, #1474, #1483, #1485, #1492 (merged); #1494 open"
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
- 4:45 PM crash: ShyDeer.home read the player's bed at spawn (chunk [2,8]) while far away; even a saved chunk load waited
  60 s behind world generation. Andy: keep the 60 s watchdog. #1466: far bed reads (ShyDeer, LifeAims, OldRules) guarded.
- Stored-position audit (8 scanners + verifiers): 14 confirmed. #1471 Kingdoms (Emigrants, BeastLairs, SpiderKinds,
  DoorPounding, Bounties, Cabs, BaneHolds, Strays, Camps, Mines), #1472 Powers (Floods, RiverIce, ArsenalStanding).
  Merged ~6:05 PM, redeploying.
- Andy's client crashed on /screenlook grey: full resource reload ran out of memory at 5 GB. Andy chose more memory:
  the CurseForge profile is now 8192 MB (set in Profile Options).
- 6:14 PM: NEW WORLD + Chunky pregen 2500 (Andy). 6:22 PM and 7:03 PM: server OutOfMemoryError (heap), both ~5 min after
  a start (first autosave) while Chunky generated; Felled's CutLogs save is only where it failed (capped at 65536).
  Heap 6 -> 8 GB (Deck start.sh and repo deck/start.sh), #1474 ChunkyCompat pauses Chunky while players are on.
  Histograms: growth is loaded-chunk data (sections, palettes, saved ticks, NBT) and StructureTemplate$StructureBlockInfo;
  live heap ~2 GB at 7:16 PM growing ~40 MB/min at ~17 cps. Watching (monitor alerts above 6.5 GB used).
- Deck start lock: a crashed server's Konsole start.sh keeps .start.lock; kill that pid (fuser .start.lock) to restart.
- 8:17 PM crash: ArrayIndexOutOfBounds in Felled's CutLogs LongLinkedOpenHashSet, called from worldgen (WorldGenRegion.setBlock
  -> ServerLevel.updatePOIOnBlockStateChange hook) on several C2ME threads at once. Same root cause as the 6:22 / 7:03 PM
  OOMs (autosave copying the corrupted linked set). #1483: FelledCutLogsMixin (@Pseudo) only notes on the server thread.
  Merged ~8:50 PM, deployed 8:52 PM: no Index -1 errors after, with Chunky at ~18 cps.
- Andy's client crashed twice on dying (8:34, 8:44 PM): SkitterCeiling.eased called getId on the new body before ID
  assignment. #1485 (client only); Andy's CurseForge profile updated (update-game.ps1), Deck redeployed to match.
- 9:48 PM: NEW WORLD + pregen 2500 on latest master (Powers 0.23.43, Kingdoms 0.8.46). The old world's renamed copy
  (world-old-20261113-214850) deleted on Andy's word. Chunky had kept the old world's task (73%) in config/chunky/tasks:
  cancelled and started fresh by hand; deck/deploy.ps1 (local, untracked) now sends chunky cancel + confirm before start.
- 10:06 PM crash: Re:Animal RedPandaEntity.isInLeaves read an unloaded neighbour while loading. #1492 RedPandaLeavesMixin.
- 10:15 PM: all 10 old world copies deleted on Andy's word (only `world` left).
- 10:32 PM crash: MCA SpawnQueue spawned a swapped villager into an unloaded chunk (getCurrentDifficultyAt). #1494
  McaSpawnQueueMixin (rotate not-ready heads, wait) - PR open, not merged/deployed yet.
- Watchdog crashes lose Chunky's in-memory task (not saved): after one, start a fresh task (chunks already made are skipped).
- 10:41 PM crash (Andy just joined): no chunk wait; 3,007 tropical fish loaded round spawn (3,363 entities) plus the first
  capital's founding on the join tick. Fish source unknown: Kingdoms' water caps (#1100) only cover natural spawning.
  Server restarted ~10:45 PM, no Chunky task. NEXT: find what spawns tropical fish in bulk on a new world and cap it.

## Still to do
- Review follow-ups (start path still makes chunks): BURNED burningVillage scan (72-block box), SKYFALL crater
  neighbours, SENTENCED seat; out-of-start budgets (shore 12, ground 6) should move to background jobs too.
- From the stall audit, not fixed: FutureStart.restore placeInWorld on every leap into an empty future;
  ZFighterAfterlife first build at x=20000; ZFighterSites tower/Lookout one-time builds; many other `level.getChunk`
  calls in Kingdoms (Couriers, Elders, Emigrants, Envoys, Roads, Speakeasy, Milestones...).
- FarmhandHome farmhouse hunt is off (SEARCH_OFF): rebuild it on Scout + ahead.
- 2026-11-13 11:38 PR #1430 is merged into master; moved to Agents/Done.

- 2026-11-14 2:37 AM: 22:41 crash cause found: WWOO's worldgen fish templates make persistent tropical fish (17,925 in the 7 regions around spawn). #1494 (MCA SpawnQueue) and #1496 (WorldgenFishMixin: kept vanilla fish swim off, at most 6 per chunk; catch-and-release fish marked from a bucket) merged. Kingdoms 0.8.50 / Powers 0.23.50 deployed with the world kept. Andy rejoined: tick time fell from 148 ms to 30 ms, fish 1,295 → 915 and falling. No Chunky task yet. Andy asked: relaunch and rejoin each time the server goes down.
- 2026-11-14 4:23 AM: two more client crashes fixed and deployed (world kept): #1510 out-of-body ghost had no entity id (Cube Animals asked it; creeper death), #1521 Kitchen corpse body rebuild caught a Re:Animal wolf load error (ghost drifting near a wolf corpse). Powers 0.23.65, Kingdoms 0.8.50, Kitchen 0.6.16 on the Deck. Andy's sessions since 4:03 AM (4:03-4:09, 4:13-4:23, 4:22-4:23) ended in plain disconnects, no crash reports; the 30-minute stable run is not reached yet. Next: fresh Chunky 2500 task (none running); vault follow-ups as before.
- 2026-11-14 7:23 AM: latest master (Powers 0.23.69, Kingdoms 0.8.70, Kitchen 0.6.16) on the Deck, world kept. Andy played 5:07-5:28, 5:32-5:53 and 6:01-6:03 AM: all normal quits, no crash or freeze, ticks 18.5 ms. One unbroken 30-minute run still not done; server idle and up since 6:03 AM. Crash watcher stopped while nobody is on.
- 2026-11-14 8:40 AM: STABLE. Master's #1536 (Kingdoms 0.8.85) wouldn't start: RunningSkull made an ItemStack in a static field during registration ("Components not bound yet"); #1550 makes it on first draw (Kingdoms 0.8.86), boot-tested then deployed with the world kept. Andy on from 8:09 AM past 8:40 with no crash or freeze. Tick average 45 ms (P95 50 ms) with Andy on: close to the limit, worth a profile. Still open: fresh Chunky 2500 task (none running); vault follow-ups as before.
- 2026-11-14 9:15 AM: 8:42 AM server crash (Lycans.unTarget read ATTACK_TARGET from brains without it) fixed in #1561; a scan of the other 16 direct getMemory reads found them all guarded. Andy asked for a new-world pbdr: deployed -NewWorld -Pregen 2500 (Powers 0.23.81, Kingdoms 0.8.94, Kitchen 0.6.21); Chunky task started. Andy's game launched and joined 9:14 AM. Andy asked for a tick profile (45 ms with him on): to run with JFR + /debug while he plays. Deploy pregen line logs a harmless "ambiguous redirect" for one Chunky command.
- 2026-11-14 9:25 AM: paused at Andy's request (he's at work). Server up on the new world, Chunky running (9.4%, about 1.5 h left). Profile captured but not yet analyzed: JFR at /tmp/tick.jfr on the Deck (tmp, may be lost on reboot) and the game's /debug profile in ~/jaymos-server/debug/. Crash watcher stopped.

## 2026-11-15 (Sunday)
- 2:44 PM: the new-world pbdr (Powers 0.24.16) failed to boot. Cause: the Stone Age recipes left their vanilla unlock advancements in place (unbound recipe holders), and scrap loot named `minecraft:chain`. Fixed in PR #1612, merged.
- 4:34 PM: during the Chunky pregen, Dynamic Waters' `leftDry`/`drainedByRavines` recursed until a StackOverflowError (16 chunks without terrain, and pregen stalled). Fixed in PR #1614 with a per-thread visiting set, checked by a verifier, merged. Deployed as Powers 0.24.18, keeping the world.
- 5:03 PM: my own mistake. `forceload add` over 42 chunks blocked the main thread and the watchdog killed the server. Restarted. Afterwards, 8 duplicate-UUID mobs and 2 CMEs appeared on the pregen's outer ring, left over from that kill.
- 5:33 PM: Chunky finished radius 2500 (99,225 chunks). No chunk errors after the fix.
- Stopped at Andy's request. The server is up and idle, and no monitors are running. The old world is kept at `world-old-20261115-144334`.
- Not yet looked at: whether the 16 failed chunks (X 2230–2440, Z 2510–2550) have terrain now.

## 2026-11-15 evening (back from the reviewer)
- 8:15 PM: server watchdog kill in `StoryGround$VillageSearch.village`. It loaded a chunk on the server thread (capital village search on the 8:08 PM new world). This was already fixed on master by #1619, but the Deck was on Kingdoms 0.9.53.
- 9:37 PM: the client crashed on startup. JVM native out of memory: the PC had only 453 MB of RAM free. Not a mod bug.
- Reviewer Claude ran out of usage partway through a 9:34 PM deploy, which left the server stopped. 9:52 PM: keep-world deploy of master (Powers 0.24.22, Kingdoms 0.9.55, Kitchen 0.6.30). Server up, game updated, watcher re-armed.
- 10:15 PM–10:52 PM: Andy online 37 minutes with no crash. The stability goal is met on Powers 0.24.22, Kingdoms 0.9.55 and Kitchen 0.6.30.
- 11:36 PM: server watchdog kill in `Castle.site`/`score`, reached from `StoryGround.citadel` (the capital's citadel measured unloaded ground on the server thread). Fixed in PR #1629: a staged wait using `getChunkNow` (not `hasChunksAt`), and no give-up fallback. Three verify rounds. Merged as Kingdoms 0.9.62 and deployed at 12:08 AM, keeping the world. Follow-up still open: the village capital path (`foundInVillage`) still gates on `hasChunksAt` at LOAD_REACH 112.
- 12:14 AM: #1629 ran live. The Kingdom of Hedmere was founded on open ground with no watchdog kill. The server fell 53–56 s behind while the 13-chunk land generated, then settled at about 52 ms a tick (16 ms by 12:40 AM).
- #1630 merged (village capital: made-land wait round the bell). It only matters on a new world, so it ships with the next deploy.
- **Client out-of-memory crashes (9:37 PM, 11:47 PM) happened while Gradle builds were running on the PC.** The 16 GB PC can't run Andy's 8 GB client and a mod build at the same time. Don't build on the PC while Andy is playing.
- 12:45 AM: the watcher was stopped at Andy's request. Server up on Powers 0.24.24 / Kingdoms 0.9.62 / Kitchen 0.6.30; #1630 is not deployed yet. The 12:31 AM client crash was a GPU out-of-memory error (GTX 960 4 GB with Complementary Unbound).
- 2:31 AM and 2:43 AM: two watchdog kills on Andy's 2:13 AM new world (no pregen) while the capital was founded. In 26.3 ServerWatchdog measures against getNextTickTime, so sustained lag counts. Cause: #1629/#1630 put a radius-13 simulating PORTAL ticket (729 chunks) on the capital at once. Fixed in #1639 (PORTAL capped at 10; the outer rings get a loading-only LAND ticket that grows one ring per try). Merged, not yet deployed. Thalfell was founded at 2:43 AM and saved, so the current world is safe. Chunky radius 1000 is running round spawn.
- 3:06 AM: server stopped at Andy's request (clean RCON stop, nobody on); watcher stopped. 2:58 AM crash: ocelot refreshDirtyAttributes NPE (iterator broke while the dirty set changed); fix PR #1641 in CI. Merged and not deployed: #1636, #1639 (and #1641 once green).
- #1641 merged (dirty attributes from a copy). Not deployed.
- 9:19 AM–10:05 AM: the Sensible MC picks deploy would not start (Stoneworks 26.3.4 and Room For Two 0.3.1 need loader 0.20; Seaworthy Boats needs Collective 8.85; the HerdPreset config was in the wrong Prickle format). Jars and config swapped by hand on the Deck and Andy's game; build fixed in #1646 (merged). The server is up.
- 10:13 AM: Andy's game crashed on launch (FWA 1.3.0 CullTaskMixin vs Entity Culling 1.12.1). FWA 1.3.7 was swapped in by hand and is in #1647, which also adds checkPackMods (CI job "Pack mods start together"; it checks declared depends/breaks with Fabric's VersionPredicate). Merged.
- 12:02 PM: server crash, Enhanced Cats casts a cat owner to Player (owner was an MCA villager, Thalfell). Fixed in #1648 (CatOwnerPlayerMixin) and merged; the Powers jar was swapped onto the Deck at 12:28 PM. Not yet confirmed live near Thalfell's cats.
- 12:45 PM: crashed again (Enhanced Cats IsAfraidOfStorm cast; six owner->Player casts, one inline in its tick handler). Andy chose to remove Enhanced Cats: #1651 merged (also drops CatOwnerPlayerMixin). Jar removed by hand from the Deck and Andy's game; server up at about 1:00 PM.
- 2:23 PM: server crash, SkyWrecks.lay NPE (toF null; no steam towns). #1652 merged; the Kingdoms jar was swapped onto the Deck at 3:14 PM. Down 2:23 PM to 3:15 PM.
- 5:49 PM: crash in Kitchen Scavengers EatPileGoal (offal pile gone). Hotfix Kitchen 0.6.32 build on the Deck; #1656 open (CI re-run after a Modrinth 429).
- 5:57/5:58/6:06 PM: 32-34 s freezes at joins. A rolling JFR (stall watcher dumps at >=20 s) showed 61% of the server thread in CustomChunks BandSpawning.forEachBand (natural spawning once per band). #1657 BandSpawnNearPlayersMixin skips bands with no player within 144 blocks. Built on the Deck (~/jaymos-src) and swapped in at 6:13 PM; watching for freezes.
- Andy: PC runs only the game; builds, test servers and profiling go on the Deck.
- 6:25 PM: Andy's Update My Game ran on my leftover branch and crawled (1 GB free RAM); he closed it. I copied the server's three Jaymo's jars into his game instead (sha256 match) and switched his checkout back to master. Client and server now: Powers 0.24.35, Kingdoms 0.9.74, Kitchen 0.6.32 hotfix. Next deploy (from master) brings the merged fixes to everyone.
- 2026-11-17: #1706 (27 hasChunksAt sites -> Loaded.made) and #1708 (RepathCooldownMixin: no re-path to a block just failed for 20 ticks; guards were 39% of villager freeze time) merged. Deck-built jars (Powers 0.24.62+#1706, Kingdoms 0.9.96 with both) wait in ~/jaymos-src for a restart when nobody is on.
