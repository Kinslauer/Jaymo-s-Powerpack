---
status: in-review
for: any
from: coordinator (Buder's Lanterns cross-reference thread)
asked: 2026-11-18
thread: https://claude.ai/code/project/chan_01638ouFWZKwEZ1ET74kBk9B?thread=cmsg_01638ouFWZKwEZ1ET74kBk9BGTKiCKJUyKUDb58Nbrp2r7
claimed_by: lanterns-05-patrol-law
pr: 1764
size: M
suggested_owns:
  - src/main/java/com/jaymospowers/power/zfighter/PatrolLaw.java (new)
  - src/main/java/com/jaymospowers/power/zfighter/ZFighterPatrol.java (hook lines)
  - changes/powers/<branch>.md
  - changes/kingdoms/<branch>.md
---
# Lanterns patch 5 (M, Powers + Kingdoms): Patrol as lawmen: warrants, arrests, a partner

**Andy's words:** "done. add it to the vault for pickup later, in order of build size" (after ticking these rows on the Buder's Lanterns pick list).

**What's wanted:**

- **Patrol warrant for crooked members** (`pt-warrant`): A Patrol member who commits crimes gets a Patrol warrant: patrolmen land to bring them in, the warrant grows with each crime, and they lose rank. _Our side:_ `ZFighterPatrol` (pursue, ranks) reading Kingdoms' `Theft.WANTED` through a compat hook.
- **Patrol members make arrests** (`pt-arrest`): A Patrol member can stop crimes they see, hogtie the crook and hand them to the town's guards for Patrol rank and the bounty. _Our side:_ `Crooks` + `Captures` + `Bounties`, credited to Patrol rank.
- **A partner patrolman** (`pt-partner`): At a set rank the Patrol gives you a partner: a Z Fighter villager who flies with you, talks and backs you up. The buddy-cop idea. _Our side:_ An MCA patrolman from `ZFighterPatrol`, led through `Followers`.

**Where to start:** ZFighterPatrol, Crooks, Captures, Bounties, Followers. Andy (video): "being a lantern, you're basically a galactic cop ... I want to add the buddy cop crime dynamic".

## Shared rules for every lanterns patch
- Source: Andy's picks (2026-11-18, 21 of 24) from **Buder's Lanterns** (CurseForge 1732134, ButterJaffa/BuDeR), mostly what the dev shows in his YouTube video https://www.youtube.com/watch?v=-Ib5PEGylyo (ch. 9 Improved Villagers, ch. 10 "GTA 6 Inspired Systems"). **All Rights Reserved, Forge 1.20.1: ideas only.** Own code, names and vanilla-style art; no Green Lantern/Oa/Corps names. Pick list: https://claude.ai/artifact/XDacSJMhawm2wZkLXtCjK7 (db `picks/andy`).
- Patches are numbered **smallest build first** (Andy: "in order of build size"): 1-3 small, 4-7 medium, 8-10 large.
- What we already have and must build on, not redo: Kingdoms `Theft` (witnesses, light/medium/heavy, guards + golems, outlaw, per-faction `WANTED`), `Approaches` (accuse: confess/fine/bribe/lie), `Bounties`, `Crooks`, `Suspicion` (named rumours, guard watch), `Recollections` (`rememberHeard` hearsay counts half), `Bearings`, `Speech`. Powers: `ZFighterPatrol` (ranks, warrants per world), `ZFighterSpaceMissions`, `PatrolWrit`, `ZFighterPodTrip`/`ZFighterFlightScreen`/`ZFighterStarMapScreen`/`ZFighterSpaceArt`, `SkyPlanets`, `HighSkyClient`, `Lens`/`LensClient`, `XenoDouble`.
- Standing rules: no new dimensions or portals unless Andy says so for that thing; pods stay rare with no recipe; no screen text stating lore; effects through MagicFx/Fx only; tuning as `static final` at the top.
- Lore first: read `Lore.md`; anything new gets a `(new)` paragraph, quoted in full to Andy for approval.
- One PR per patch with `changes/<mod>/<branch>.md`; never touch `version=`, MODLOGs or codemap. Andy tests by merging. Run 2 patches at a time.

## Notes
- 2026-11-18 20:42 lanterns-05-patrol-law's claim had no sign of work for 30 min: handed back as open.
