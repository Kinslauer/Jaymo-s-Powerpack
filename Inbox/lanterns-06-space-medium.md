---
status: in-review
for: any
from: coordinator (Buder's Lanterns cross-reference thread)
asked: 2026-11-18
thread: https://claude.ai/code/project/chan_01638ouFWZKwEZ1ET74kBk9B?thread=cmsg_01638ouFWZKwEZ1ET74kBk9BGTKiCKJUyKUDb58Nbrp2r7
claimed_by: lanterns-06-space-medium
pr: 1766
size: M
suggested_owns:
  - src/main/java/com/jaymospowers/power/zfighter/ZFighterPodTrip.java (hook lines)
  - src/client/java/com/jaymospowers/client/power/zfighter/ZFighterFlightScreen.java
  - changes/powers/<branch>.md
---
# Lanterns patch 6 (M, Powers): Space: a black hole, flying to a world on ki

**Andy's words:** "done. add it to the vault for pickup later, in order of build size" (after ticking these rows on the Buder's Lanterns pick list).

**What's wanted:**

- **A black hole out past the worlds** (`sp-hole`): A black hole on the star map that a pod can't fly through; trips near it bend off course, and the flight screen bends the light. _Our side:_ `Lens`/`LensClient` art on `ZFighterFlightScreen`; a far-world mission hook.
- **Fly to a world on your own ki** (`sp-ki`): At a very high power level a Z Fighter can fly to a near world without a pod, burning a lot of ki. Pods stay rare for everyone else. _Our side:_ `ZFighterPodTrip` run without the pod; set the power gate with Andy.

**Where to start:** ZFighterPodTrip, ZFighterFlightScreen, Lens, ZFighterPowerLevel.

## Shared rules for every lanterns patch
- Source: Andy's picks (2026-11-18, 21 of 24) from **Buder's Lanterns** (CurseForge 1732134, ButterJaffa/BuDeR), mostly what the dev shows in his YouTube video https://www.youtube.com/watch?v=-Ib5PEGylyo (ch. 9 Improved Villagers, ch. 10 "GTA 6 Inspired Systems"). **All Rights Reserved, Forge 1.20.1: ideas only.** Own code, names and vanilla-style art; no Green Lantern/Oa/Corps names. Pick list: https://claude.ai/artifact/XDacSJMhawm2wZkLXtCjK7 (db `picks/andy`).
- Patches are numbered **smallest build first** (Andy: "in order of build size"): 1-3 small, 4-7 medium, 8-10 large.
- What we already have and must build on, not redo: Kingdoms `Theft` (witnesses, light/medium/heavy, guards + golems, outlaw, per-faction `WANTED`), `Approaches` (accuse: confess/fine/bribe/lie), `Bounties`, `Crooks`, `Suspicion` (named rumours, guard watch), `Recollections` (`rememberHeard` hearsay counts half), `Bearings`, `Speech`. Powers: `ZFighterPatrol` (ranks, warrants per world), `ZFighterSpaceMissions`, `PatrolWrit`, `ZFighterPodTrip`/`ZFighterFlightScreen`/`ZFighterStarMapScreen`/`ZFighterSpaceArt`, `SkyPlanets`, `HighSkyClient`, `Lens`/`LensClient`, `XenoDouble`.
- Standing rules: no new dimensions or portals unless Andy says so for that thing; pods stay rare with no recipe; no screen text stating lore; effects through MagicFx/Fx only; tuning as `static final` at the top.
- Lore first: read `Lore.md`; anything new gets a `(new)` paragraph, quoted in full to Andy for approval.
- One PR per patch with `changes/<mod>/<branch>.md`; never touch `version=`, MODLOGs or codemap. Andy tests by merging. Run 2 patches at a time.

## Notes
- 2026-11-18 19:44 Andy approved the ki-crossing numbers as they are (50,000 power, 80% ki) and the Hollow lore.
