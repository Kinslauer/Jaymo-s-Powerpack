---
name: flintlock-spare-draw
description: Q on your own flintlock draws a spare from inventory (armory/FlintlockDraw), PR #522 MERGED 2026-10-25
metadata:
  type: project
  modified: 2026-10-25T18:36:44.493Z
---
2026-10-25 Andy: dropping a flintlock makes you reach for any others you carry, like the Void Armory pair, but nothing respawns.
PR #522 (branch claude/project-thread-twq15r, MERGED 2026-10-25 21:12Z; opened as 0.16.25, merger may have renumbered): `armory/FlintlockDraw` (synced `flintlock_draw` attachment, 6-tick reach, 12-tick whole draw), hooked in `ServerPlayerDropMixin` (HEAD/RETURN of drop(Z)); pose in `ArsenalArmPose.applyDraw`. Any item id containing "flintlock" counts, never Summoned ones; inventory only (not the backpack); loaded first. Jar /mnt/project-files/flintlock-draw/. Not playtested; belt/draw angles are guesses.
Related: [[tacz-guns]], [[arsenal-pocket-dimension]].
