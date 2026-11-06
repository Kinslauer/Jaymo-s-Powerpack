---
name: yank-heave
description: Yank/Web Whip moves swing the whole upper body (waist turn, planted legs); PR #216, pack 0.11.3 (2026-10-15)
metadata:
  type: project
---
Andy 2026-10-15: Yank overhead spin and other Yank moves should sway the upper body with gravity, legs stable, not only arms.
PR #216 (branch claude/project-thread-v7ixgd, pack 0.11.3; #212/#213 hold 0.11.2), not playtested. Jar /mnt/project-files/yank-heave/.
**How to apply:** WebArms "heave" block: torso spring turns body/head/shoulder pivots about a waist 12 units below the neck (ModelPart uses Quaternionf.rotationZYX(z,y,x); Model.setupAnim calls resetPose). shoulder() follows the turn so lines leave the posed hand. ModelPoses can run twice a frame: set/lerp parts, never +=.
Related: [[web-lines-weight]], [[pose-takeover-emf]].
