---
name: chrono-overclock-toggle
description: Chrono V (Overclock) is a toggle once all four abilities are unlocked + lava run + straight wall run, PR #234 (pack 0.11.40), 2026-10-15
metadata:
  type: project
  modified: 2026-10-15T08:55:36.779Z
---
2026-10-15 Andy: "chrono V is a toggle at max rank". For Chrono, "max rank" = all four abilities unlocked (Mastery.unlocked >= ALL), NOT ascension (Andy corrected this). PR #234 (pack 0.11.37): V on with no limit, V again away from a wall switches off and starts the cooldown; V at a wall still phases. `ChronoState.overclockHeld`. Same PR: overclocked sprint runs on lava as well as water (ChronoSpeed.runsOn). Wall run (Andy: "straight up the wall", "animated running like gravity does"): no sideways drift, no time cap, hop onto the top; synced ChronoSpeed.WALL_RUN attachment drawn by GravityWalkPose facing up, walk animation forced. Round 3 (09:45Z): Shift while overclocked = plain walking speed (ChronoSpeed.control). Time Flow: landed RMB counter -> 2 s intangible; blow through you -> red edges + red space-bar cue overhead (DODGE attachment, ChronoSpaceCue); Space = free SPIN kick (5-block knockback); Space again (during/12 ticks after) spends 1 of 3 roll charges (refill on entering Z) on PUSH: both arms out + Stasis Field where you stand (Andy corrected: charges are for this follow-up). Shakes rise per barrage/Chrono Punch hit; RMB counter shakes. PR #234 MERGED 2026-10-15 10:02Z as pack 0.11.40. Not playtested. Jar /mnt/project-files/chrono-toggle/. Related: [[flash-shield]], [[chrono-g-reset]].
