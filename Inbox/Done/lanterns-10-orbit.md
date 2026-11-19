---
status: done
for: any
from: coordinator (Buder's Lanterns cross-reference thread)
asked: 2026-11-18
thread: https://claude.ai/code/project/chan_01638ouFWZKwEZ1ET74kBk9B?thread=cmsg_01638ouFWZKwEZ1ET74kBk9BGTKiCKJUyKUDb58Nbrp2r7
claimed_by: lanterns-10-orbit
pr: 1772
size: L
suggested_owns:
  - src/client/java/com/jaymospowers/client/power/zfighter/sky/HighSkyClient.java
  - src/main/java/com/jaymospowers/power/zfighter/Orbit.java (new)
  - changes/powers/<branch>.md
---
# Lanterns patch 10 (L, Powers): Orbit above a world: the high sky made into near space

**Andy's words:** "done. add it to the vault for pickup later, in order of build size" (after ticking these rows on the Buder's Lanterns pick list).

**What's wanted:**

- **Orbit above a world** (`sp-orbit`): Fly up past the high sky and the world curves away below you; flying fast in orbit carries you far across the map, and you come down wherever you dive. _Our side:_ **Andy's note:** "this is what i want the high sky to look like". So this reworks `HighSkyClient`'s look first (curved world below, space above, the sun hard and bright), then the fast-travel orbit for Z Fighter flight and pods.

**Where to start:** HighSkyClient, SkyPlanets, ZFighterFlightClient, ZFighterPodTrip. Their screenshots: media.forgecdn.net/attachments/2023/152/01-orbital-flight-png.png and 08-ringed-planet-png.png (look only).

## Shared rules for every lanterns patch
- Source: Andy's picks (2026-11-18, 21 of 24) from **Buder's Lanterns** (CurseForge 1732134, ButterJaffa/BuDeR), mostly what the dev shows in his YouTube video https://www.youtube.com/watch?v=-Ib5PEGylyo (ch. 9 Improved Villagers, ch. 10 "GTA 6 Inspired Systems"). **All Rights Reserved, Forge 1.20.1: ideas only.** Own code, names and vanilla-style art; no Green Lantern/Oa/Corps names. Pick list: https://claude.ai/artifact/XDacSJMhawm2wZkLXtCjK7 (db `picks/andy`).
- Patches are numbered **smallest build first** (Andy: "in order of build size"): 1-3 small, 4-7 medium, 8-10 large.
- What we already have and must build on, not redo: Kingdoms `Theft` (witnesses, light/medium/heavy, guards + golems, outlaw, per-faction `WANTED`), `Approaches` (accuse: confess/fine/bribe/lie), `Bounties`, `Crooks`, `Suspicion` (named rumours, guard watch), `Recollections` (`rememberHeard` hearsay counts half), `Bearings`, `Speech`. Powers: `ZFighterPatrol` (ranks, warrants per world), `ZFighterSpaceMissions`, `PatrolWrit`, `ZFighterPodTrip`/`ZFighterFlightScreen`/`ZFighterStarMapScreen`/`ZFighterSpaceArt`, `SkyPlanets`, `HighSkyClient`, `Lens`/`LensClient`, `XenoDouble`.
- Standing rules: no new dimensions or portals unless Andy says so for that thing; pods stay rare with no recipe; no screen text stating lore; effects through MagicFx/Fx only; tuning as `static final` at the top.
- Lore first: read `Lore.md`; anything new gets a `(new)` paragraph, quoted in full to Andy for approval.
- One PR per patch with `changes/<mod>/<branch>.md`; never touch `version=`, MODLOGs or codemap. Andy tests by merging. Run 2 patches at a time.

## Notes
- 2026-11-18 20:05 PR #1772 is merged into master; moved to Inbox/Done.
