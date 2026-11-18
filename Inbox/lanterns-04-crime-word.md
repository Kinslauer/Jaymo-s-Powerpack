---
status: claimed
for: any
from: coordinator (Buder's Lanterns cross-reference thread)
asked: 2026-11-18
thread: https://claude.ai/code/project/chan_01638ouFWZKwEZ1ET74kBk9B?thread=cmsg_01638ouFWZKwEZ1ET74kBk9BGTKiCKJUyKUDb58Nbrp2r7
claimed_by: lanterns-04-crime-word
pr: 
size: M
suggested_owns:
  - kingdoms/src/main/java/com/jaymoskingdoms/CrimeWord.java (new)
  - kingdoms/src/main/java/com/jaymoskingdoms/Theft.java (hook lines)
  - kingdoms/src/main/java/com/jaymoskingdoms/Recollections.java (hook lines)
  - changes/kingdoms/<branch>.md
---
# Lanterns patch 4 (M, Kingdoms): Crime: word that travels

**Andy's words:** "done. add it to the vault for pickup later, in order of build size" (after ticking these rows on the Buder's Lanterns pick list).

**What's wanted:**

- **The witness runs to tell** (`cr-runner`): A witness runs to the nearest guard or crowd before the law is on you; catch, calm or stop them first and nothing happens. A cry within earshot still counts at once. _Our side:_ `Theft#commit` waits for the runner; guards and golems turn when the runner arrives.
- **Crime gossip goes house to house** (`cr-gossip`): People who heard tell others over the next day; hearsay counts half and makes them wary rather than hostile. _Our side:_ `Recollections#rememberHeard` (used today for kept/broken promises) gets crime kinds; `Suspicion` gossip lines voice it.
- **Talk the witness out of it** (`cr-hush`): Reach the witness before they've told anyone: plead, pay them off, or stare them down, each costing warmth or trust by how it goes. _Our side:_ An `Approaches` menu on the witness; `Bearings`; Will staredowns for the threat.
- **Word goes down the road** (`cr-road`): Other towns don't know at once: news of a heavy crime travels with riders, coaches and rickshaws along the King's Roads, or reaches the next bounty board days later. _Our side:_ `Roads` + travel drivers carry it; `Bounties` posts it.

**Where to start:** Theft (commit, witnesses, WANTED), Approaches, Recollections, Bearings, Roads, Bounties. Andy (video): "that villager is the one that remembers you and is going to spread the rumors about you ... All of that can be gone if you talk him out of it or pay up".

## Shared rules for every lanterns patch
- Source: Andy's picks (2026-11-18, 21 of 24) from **Buder's Lanterns** (CurseForge 1732134, ButterJaffa/BuDeR), mostly what the dev shows in his YouTube video https://www.youtube.com/watch?v=-Ib5PEGylyo (ch. 9 Improved Villagers, ch. 10 "GTA 6 Inspired Systems"). **All Rights Reserved, Forge 1.20.1: ideas only.** Own code, names and vanilla-style art; no Green Lantern/Oa/Corps names. Pick list: https://claude.ai/artifact/XDacSJMhawm2wZkLXtCjK7 (db `picks/andy`).
- Patches are numbered **smallest build first** (Andy: "in order of build size"): 1-3 small, 4-7 medium, 8-10 large.
- What we already have and must build on, not redo: Kingdoms `Theft` (witnesses, light/medium/heavy, guards + golems, outlaw, per-faction `WANTED`), `Approaches` (accuse: confess/fine/bribe/lie), `Bounties`, `Crooks`, `Suspicion` (named rumours, guard watch), `Recollections` (`rememberHeard` hearsay counts half), `Bearings`, `Speech`. Powers: `ZFighterPatrol` (ranks, warrants per world), `ZFighterSpaceMissions`, `PatrolWrit`, `ZFighterPodTrip`/`ZFighterFlightScreen`/`ZFighterStarMapScreen`/`ZFighterSpaceArt`, `SkyPlanets`, `HighSkyClient`, `Lens`/`LensClient`, `XenoDouble`.
- Standing rules: no new dimensions or portals unless Andy says so for that thing; pods stay rare with no recipe; no screen text stating lore; effects through MagicFx/Fx only; tuning as `static final` at the top.
- Lore first: read `Lore.md`; anything new gets a `(new)` paragraph, quoted in full to Andy for approval.
- One PR per patch with `changes/<mod>/<branch>.md`; never touch `version=`, MODLOGs or codemap. Andy tests by merging. Run 2 patches at a time.

## Notes
- 
