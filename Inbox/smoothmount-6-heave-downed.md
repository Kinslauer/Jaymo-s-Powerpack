---
status: open
for: hermes
from: claude-cloud-smoothmount-thread
asked: 2026-11-19
claimed_by: 
pr: 
---

# SmoothMount climbing, patch 6. Heave a downed friend onto a horse (Kitchen + Powers Carry + Downed)

**Andy (2026-11-19):** ticked all 14 rows on the SmoothMount pick page. Full plan: `/mnt/project-files/smoothmount/Climbing-Plan.md` (cloud) — the plan's intro is copied below.

Andy ticked all 14 rows on the pick page (https://claude.ai/artifact/5V7rmUUfGshcPFy32EQ5Qa, db picks/andy, saved 2026-11-19 18:20Z).
Source: SmoothMount (Ender_Fake, LGPL-3.0, Fabric 1.21.11 only, client only). Ideas only: write our own code, do not copy
its code or its keyframes. Our own names. Clips are Blockbench (GeckoLib .animation.json) files played on the vanilla
player model through Powers' `client/KeyframeClip` (as `CrawlPose` plays `player_getup`), pausing Fresh Animations via
`client/EmfCompat#hold` while they play.

Andy's note: "if our horse models interfere tell me and why, I might be willing to go to default". They don't: the climbs
are on the rider, and the horse-side poses (kneel, sidestep, trunk lift) go into our own BreedHorseModel / WarElephantModel,
which is easier than vanilla. If a builder finds a real clash, stop and set the task `blocked` with the reason.

Order: patch 1 first (the others reuse its pose class). Two tracks at a time: 1 + 6, then 2 + 4, then 3 + 5.

## 6. Heave a downed friend onto a horse (Kitchen + Powers Carry + Downed)
Pick: heave-downed.
- Carrying a downed (still living) player or folk, use a horse/donkey/mule/camel: you heave them up and they lie slung over
  its back, like corpses already can (Kitchen JaymosKitchen 0.4.48 sling, CorpseEntity.slingOnto). They ride slung until
  they come to, then can sit up or get off. Respect the downed rules (no respawn roll while carried, revive at safety).

One PR, branch `hermes/smoothmount-6-heave-downed`, change note `changes/<mod>/<branch>.md`, never `version=`/MODLOG/codemap. Check every API against 26.3 sources. If our horse models really clash with this, set the task `blocked` with the reason (Andy may go back to default models).
