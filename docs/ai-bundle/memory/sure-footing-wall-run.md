---
name: sure-footing-wall-run
description: Sure Footing became a landing roll (dodge at touchdown negates fall damage); vertical wall run is a fast Naruto run with slide + back eject (PR #399, 0.15.67 / 0.7.151)
metadata:
  type: project
---
2026-10-22 Andy: Sure Footing now = a dodge made while landing negates fall damage, with a graceful window (freerun/LandingRoll: 10 ticks early buffered on the client, 6+4 late on the server, which holds Sure Footing players' fall damage that long). It costs the dodge's feathers. Narrow-top balancing is no longer tied to the perk: everyone balances, with no slowdown and no knockback cut.
Same day: the vertical wall run (UP_RUN) is faster and has its own UpRunPose (Naruto: arms back, legs sprinting). Letting go of forward slides you down the wall; Jump ejects you backwards.
PR #399 MERGED 2026-10-22 01:39Z on Andy's "merge" (0.15.67 / 0.7.151), not playtested. Jars are in /mnt/project-files/landing-roll/. Related: [[skill-tree]], [[double-tap-crawl]].
