---
status: in-review
for: any
from: coordinator (Buder's Lanterns cross-reference thread)
asked: 2026-11-18
thread: https://claude.ai/code/project/chan_01638ouFWZKwEZ1ET74kBk9B?thread=cmsg_01638ouFWZKwEZ1ET74kBk9BGTKiCKJUyKUDb58Nbrp2r7
claimed_by: lanterns-09-talk-typed
pr: 1770
size: L
suggested_owns:
  - kingdoms/src/main/java/com/jaymoskingdoms/AskTopics.java (new)
  - kingdoms/src/client/java/com/jaymoskingdoms/client/TalkScreen.java (hook lines)
  - kingdoms/src/main/resources/assets/jaymoskingdoms/lang/parts/ask.json (new)
  - changes/kingdoms/<branch>.md
---
# Lanterns patch 9 (L, Kingdoms): Ask-a-question box with suggested follow-ups

**Andy's words:** "done. add it to the vault for pickup later, in order of build size" (after ticking these rows on the Buder's Lanterns pick list).

**What's wanted:**

- **Ask-a-question box** (`tk-typed`): Type anything on a villager's talk screen; a local matcher finds the topic (name, work, family, town, rumours, a crime, directions, weather) and answers in that villager's way. Nothing goes online. _Our side:_ `TalkScreen`/`TalkErrands` + `Recollections`, `Bearings`, `Tempers` for tone.
- **Suggested follow-ups** (`tk-follow`): A side panel shows three or four questions that fit the topic, to click instead of typing. _Our side:_ Same screen, built from the matched topic.

**Where to start:** TalkScreen, TalkErrands, ChatPrompts (what the MCA AI is told: reuse as the facts a villager knows). Andy: the video's talk system is "similar to what i want". Lore delivery rule: oblique hints only.

## Shared rules for every lanterns patch
- Source: Andy's picks (2026-11-18, 21 of 24) from **Buder's Lanterns** (CurseForge 1732134, ButterJaffa/BuDeR), mostly what the dev shows in his YouTube video https://www.youtube.com/watch?v=-Ib5PEGylyo (ch. 9 Improved Villagers, ch. 10 "GTA 6 Inspired Systems"). **All Rights Reserved, Forge 1.20.1: ideas only.** Own code, names and vanilla-style art; no Green Lantern/Oa/Corps names. Pick list: https://claude.ai/artifact/XDacSJMhawm2wZkLXtCjK7 (db `picks/andy`).
- Patches are numbered **smallest build first** (Andy: "in order of build size"): 1-3 small, 4-7 medium, 8-10 large.
- What we already have and must build on, not redo: Kingdoms `Theft` (witnesses, light/medium/heavy, guards + golems, outlaw, per-faction `WANTED`), `Approaches` (accuse: confess/fine/bribe/lie), `Bounties`, `Crooks`, `Suspicion` (named rumours, guard watch), `Recollections` (`rememberHeard` hearsay counts half), `Bearings`, `Speech`. Powers: `ZFighterPatrol` (ranks, warrants per world), `ZFighterSpaceMissions`, `PatrolWrit`, `ZFighterPodTrip`/`ZFighterFlightScreen`/`ZFighterStarMapScreen`/`ZFighterSpaceArt`, `SkyPlanets`, `HighSkyClient`, `Lens`/`LensClient`, `XenoDouble`.
- Standing rules: no new dimensions or portals unless Andy says so for that thing; pods stay rare with no recipe; no screen text stating lore; effects through MagicFx/Fx only; tuning as `static final` at the top.
- Lore first: read `Lore.md`; anything new gets a `(new)` paragraph, quoted in full to Andy for approval.
- One PR per patch with `changes/<mod>/<branch>.md`; never touch `version=`, MODLOGs or codemap. Andy tests by merging. Run 2 patches at a time.

## Notes
- 
