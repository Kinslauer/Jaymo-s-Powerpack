---
name: sense-counters
description: PR #131 (pack 0.10.2): sword stones 3000 apart; Web sense charges by level + 9 web / 9 symbiote right-click counters
metadata:
  type: project
  modified: 2026-10-14T01:12:38.993Z
---
2026-10-14 Andy asked: swords in stone never within 3000 blocks of each other; Web gets sense charges as it levels (max at ascended); right click after a sense dodge counters with 9 web/physical moves, symbiote suit has 9 of its own (tendrils or giant head jaws).
PR #131 MERGED 2026-10-14 as pack 0.10.2 (branch claude/sense-counters-ko2p13, pack 0.10.2; #129 holds 0.10.0, #130 0.10.1). Not playtested.
- SwordStone: seed-derived cell grid (SPACING 3000), candidate stands unless an outranking neighbour is closer; owner() picks one blade. Swords are now much rarer.
- WebAscension.senseCharges: CHARGES_BY_UNLOCKED {1,1,2,3,4}, 5 ascended; dodges no longer need ascension (outlines still do).
- WebCounter: WINDOW 30 ticks; maw moves only bite, never feed (symbiote never feeds on the living, see [[symbiote-rigid-barrage]]).
2026-10-14 05:00Z follow-up, draft PR #136 (pack 0.10.7; 0.10.5 taken by #133, 0.10.6 by #134/#135): Andy wanted no counter text; Spider-Sense = three faint lightning bolts flashing overhead (WebSenseBolts, READY synced to all). Takeovers must not stop short: no timer, run until success or struggle (holdForMeal keeps the wheel through grab/carry/meal). Right click (no Sneak) takes a tendril-held corpse into your arms (WebTendrils.CarryHeld).
MERGED 2026-10-14 ~05:31Z as pack 0.10.10. Andy wants breaking free FAR harder (BREAK_FREE_BASE 80, PER_ABILITY 6, STRUGGLE_FADE 0.25).
2026-10-14 05:5xZ round 3, PR #139 MERGED 05:50Z as pack 0.10.12: Jump at any point cuts a Web Zip/Yank-to (fresh press only; server drops line on main-hand Swing let-go); straight-down wing freedive landing = HERO_LAND + ground-breaking WebSlam.diveSlam (server-checked fall).
