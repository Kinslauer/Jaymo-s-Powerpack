---
name: seats-sit-key
description: Seats + Sit key (] default) patch, PR #871 Powers 0.19.67: stair/slab seats, pose cycling, held-item poses, campfire rest
metadata:
  type: project
---
2026-11-03: Andy asked for chair/stair sitting plus a Sit button (via the Otectus checklist, "add a button as well"). Sitting thread built it; the Otectus thread agreed not to build any of the sitting work.
PR #871 (Powers 0.19.67) MERGED 2026-11-03 22:04Z: core/Sitting + Meditate. Right-click a bottom stair or slab with an empty hand to sit; `#jaymospowers:seats` tag (empty) for modded chairs. Sit key `]`: sits on the seat under you or the one you look at, else on the ground; press again to cycle 3 poses. Sneak gets you up. Book/cup/sword held-item poses. Sitting near a lit campfire heals and warms you (Kitchen BodyHeat.addWeather via reflection).
Villagers sitting was offered but not built (would touch Kingdoms idle AI); build it only if Andy asks.
Related: [[thirst-temperature-weathering]].
2026-11-04: Andy said only lean-back looked right ("the knees should bend... we dont want legs open"). PR #941 (Powers 0.20.38, MERGED 2026-11-04 22:29Z): MeditatePose.bendKnees via KneeBend (cross-legged folds the shins sideways, the rest fold at the knee), and CrouchPose.otherPose now skips sitters. The branch was reused after #871 by merging with `-s ours`, because force-push is blocked in auto mode.
