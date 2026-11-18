---
status: claimed
for: any
from: coordinator (Buder's Lanterns cross-reference thread)
asked: 2026-11-18
thread: https://claude.ai/code/project/chan_01638ouFWZKwEZ1ET74kBk9B?thread=cmsg_01638ouFWZKwEZ1ET74kBk9BGTKiCKJUyKUDb58Nbrp2r7
claimed_by: lanterns-07-talk-chat
pr: 
size: M
suggested_owns:
  - kingdoms/src/main/java/com/jaymoskingdoms/ChatTalk.java (new)
  - src/main/java/com/jaymospowers/power/xenoform/XenoDouble.java (hook lines)
  - changes/kingdoms/<branch>.md
  - changes/powers/<branch>.md
---
# Lanterns patch 7 (M, Kingdoms + Powers): Talk through chat; Duplex doubles take orders

**Andy's words:** "done. add it to the vault for pickup later, in order of build size" (after ticking these rows on the Buder's Lanterns pick list).

**What's wanted:**

- **Talk through chat** (`tk-chat`): Look at a villager within 8 blocks, or start a chat line with their name (12 blocks), and they answer over their head; short replies keep it going. _Our side:_ A chat hook + `Speech` bubbles; MCA's chat AI stays the online option. Shares the topic matcher with patch 9 if that lands first, else a small one here that patch 9 grows.
- **Duplex doubles talk and do chores** (`tk-double`): Duplex doubles take spoken orders: follow, wait, scout, collect drops, mine open blocks, fetch from a chest, fight. _Our side:_ `XenoDouble` goals + the chat/talk matcher.

**Where to start:** Speech, McaChatAI mixins, XenoDouble, Duplex.

## Shared rules for every lanterns patch
- Source: Andy's picks (2026-11-18, 21 of 24) from **Buder's Lanterns** (CurseForge 1732134, ButterJaffa/BuDeR), mostly what the dev shows in his YouTube video https://www.youtube.com/watch?v=-Ib5PEGylyo (ch. 9 Improved Villagers, ch. 10 "GTA 6 Inspired Systems"). **All Rights Reserved, Forge 1.20.1: ideas only.** Own code, names and vanilla-style art; no Green Lantern/Oa/Corps names. Pick list: https://claude.ai/artifact/XDacSJMhawm2wZkLXtCjK7 (db `picks/andy`).
- Patches are numbered **smallest build first** (Andy: "in order of build size"): 1-3 small, 4-7 medium, 8-10 large.
- What we already have and must build on, not redo: Kingdoms `Theft` (witnesses, light/medium/heavy, guards + golems, outlaw, per-faction `WANTED`), `Approaches` (accuse: confess/fine/bribe/lie), `Bounties`, `Crooks`, `Suspicion` (named rumours, guard watch), `Recollections` (`rememberHeard` hearsay counts half), `Bearings`, `Speech`. Powers: `ZFighterPatrol` (ranks, warrants per world), `ZFighterSpaceMissions`, `PatrolWrit`, `ZFighterPodTrip`/`ZFighterFlightScreen`/`ZFighterStarMapScreen`/`ZFighterSpaceArt`, `SkyPlanets`, `HighSkyClient`, `Lens`/`LensClient`, `XenoDouble`.
- Standing rules: no new dimensions or portals unless Andy says so for that thing; pods stay rare with no recipe; no screen text stating lore; effects through MagicFx/Fx only; tuning as `static final` at the top.
- Lore first: read `Lore.md`; anything new gets a `(new)` paragraph, quoted in full to Andy for approval.
- One PR per patch with `changes/<mod>/<branch>.md`; never touch `version=`, MODLOGs or codemap. Andy tests by merging. Run 2 patches at a time.

## Notes
- 
