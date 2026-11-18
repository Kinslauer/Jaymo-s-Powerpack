---
status: claimed
for: any
from: coordinator (Buder's Lanterns cross-reference thread)
asked: 2026-11-18
thread: https://claude.ai/code/project/chan_01638ouFWZKwEZ1ET74kBk9B?thread=cmsg_01638ouFWZKwEZ1ET74kBk9BGTKiCKJUyKUDb58Nbrp2r7
claimed_by: lanterns-08-dream
pr: 1769
size: L
suggested_owns:
  - src/main/java/com/jaymospowers/core/DreamCall.java (new)
  - src/client/java/com/jaymospowers/client/DreamView.java (new)
  - changes/powers/<branch>.md
---
# Lanterns patch 8 (L, Powers + Kingdoms): Called in a dream: awakening through a dream or nightmare

**Andy's words:** "done. add it to the vault for pickup later, in order of build size" (after ticking these rows on the Buder's Lanterns pick list).

**What's wanted:**

- **Called in a dream** (`ot-dream`): A power finds you while you sleep. _Our side:_ **Andy's note:** "there should be a chance for the view to pan up as you are sleeping indicating the dream, and it takes you into a grue like realm, it is dark during a nightmare, and you awaken a corrupted power if you survive, and a dream is peaceful, and you awaken your power the next day". Kingdoms' `NightCam` already lifts the camera off the pillow at night; start there.

**Where to start:** NightCam / Timelapse (Kingdoms), power awakening (gifted villagers, life story), corrupted variants. **Ask Andy first:** the "grue like realm" would be a new realm, which the standing rule forbids unless he asks for it. He did ask here, so confirm: a real dream dimension (his exception, like Cloud Sea), or a dream arena built where you sleep (dark world, fog, nothing else loaded). Also needs a Lore.md `(new)` paragraph (who sends the dream, why a nightmare corrupts) quoted to Andy.

## Shared rules for every lanterns patch
- Source: Andy's picks (2026-11-18, 21 of 24) from **Buder's Lanterns** (CurseForge 1732134, ButterJaffa/BuDeR), mostly what the dev shows in his YouTube video https://www.youtube.com/watch?v=-Ib5PEGylyo (ch. 9 Improved Villagers, ch. 10 "GTA 6 Inspired Systems"). **All Rights Reserved, Forge 1.20.1: ideas only.** Own code, names and vanilla-style art; no Green Lantern/Oa/Corps names. Pick list: https://claude.ai/artifact/XDacSJMhawm2wZkLXtCjK7 (db `picks/andy`).
- Patches are numbered **smallest build first** (Andy: "in order of build size"): 1-3 small, 4-7 medium, 8-10 large.
- What we already have and must build on, not redo: Kingdoms `Theft` (witnesses, light/medium/heavy, guards + golems, outlaw, per-faction `WANTED`), `Approaches` (accuse: confess/fine/bribe/lie), `Bounties`, `Crooks`, `Suspicion` (named rumours, guard watch), `Recollections` (`rememberHeard` hearsay counts half), `Bearings`, `Speech`. Powers: `ZFighterPatrol` (ranks, warrants per world), `ZFighterSpaceMissions`, `PatrolWrit`, `ZFighterPodTrip`/`ZFighterFlightScreen`/`ZFighterStarMapScreen`/`ZFighterSpaceArt`, `SkyPlanets`, `HighSkyClient`, `Lens`/`LensClient`, `XenoDouble`.
- Standing rules: no new dimensions or portals unless Andy says so for that thing; pods stay rare with no recipe; no screen text stating lore; effects through MagicFx/Fx only; tuning as `static final` at the top.
- Lore first: read `Lore.md`; anything new gets a `(new)` paragraph, quoted in full to Andy for approval.
- One PR per patch with `changes/<mod>/<branch>.md`; never touch `version=`, MODLOGs or codemap. Andy tests by merging. Run 2 patches at a time.

## Notes
- 
