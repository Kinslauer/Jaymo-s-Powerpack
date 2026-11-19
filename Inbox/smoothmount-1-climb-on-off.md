---
status: writing
for: hermes
from: claude-cloud-smoothmount-thread
asked: 2026-11-19
claimed_by: hermes-a
pr: 1869
writer: hermes-writer
---

# SmoothMount climbing, patch 1. Climb on, climb off, camera glide (Powers client + a small server hold)

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

## 1. Climb on, climb off, camera glide (Powers client + a small server hold)
Picks: climb-on, climb-off, camera-glide, pig, armour-weight.
- Climb on by angle to the mount: from the left (foot in stirrup, swing the right leg over), from the right (mirror), from
  behind (vault onto the rump, slide forward). Roughly 0.5-0.75 s. Horses, donkeys, mules, our breeds, saddled pigs (a low
  step-over).
- Climb off: forward or back, toward the spot vanilla/Horseman sets you down, ending exactly there.
- Camera: ease from standing eye height to the saddle and back (~0.3-0.45 s) instead of snapping; first person follows the
  head through the clip.
- Armour: climb time scales with armour points (cloth ~1x, full plate ~2x). The horse must stand still. Server side, the
  ride starts only when the climb ends, so a slow climb can be interrupted by a hit (cancel the climb, no ride).
- Other players see it too (it plays for any rider in view, not only you).
- New class e.g. `client/MountClimbPose`; clips in `geckolib/animations/entity/player_mount_*.animation.json`.

One PR, branch `hermes/smoothmount-1-climb-on-off`, change note `changes/<mod>/<branch>.md`, never `version=`/MODLOG/codemap. Check every API against 26.3 sources. If our horse models really clash with this, set the task `blocked` with the reason (Andy may go back to default models).

## Notes
- 2026-11-19 16:45 Text pass: reviewed the change note changes/powers/smoothmount-1-climb-on-off.md; already plain, brief and player-facing (what the player sees, ending Built; not play-tested in game), so no rewrite needed. The PR adds no lang parts, dialogue, hints, item/ability text, chat lines or screens; all changed Java/JSON strings are animation, packet and mixin keys. No code changed.
