---
name: carry-left-for-dead
description: PR #1046 (Powers 0.21.7): left-for-dead bodies lie until sneak-held come-to or a revive somewhere safe; live small animals liftable, some bite
metadata:
  type: project
  modified: 2026-11-05T19:35:16.001Z
---
2026-11-05 Andy: "your body should persist in the world until come to ... if you do not press anything, you will not get the respawn roll and instead get a notification that you are being carried". Also "you can pick up live animals, but not all of them like it, snakes for example will bite you".
PR #1046 (branch claude/project-thread-urz954), built, not playtested, MERGED 2026-11-05 19:38Z (master 76f89e6).
- Mortality: Wake.chosen; lying() until sneak held 2 s (COME_TO_TICKS) -> wake roll. Carried: WAKE_LINE "carried". Revive (Downed revives) blocked if Enemy within SAFE_RANGE 12; any getting up = broughtRound (injury + SURVIVOR, no roll). Mobs drop target, MobExecutions.SPARE. Logout while lying = come to in place; JOIN clamps day-long knock-outs. Turned deaths unchanged.
- My readings: no auto-timeout (lie forever if nobody presses); "safe" = no hostile within 12.
- CarryTemper: animals <=0.8x1.0 liftable; tags bites/scratches/squirms_when_held; tamed/babies calm.
Related: [[reincarnation]], [[downed-self-rise]], [[lift-revive-execute-inputs]].
