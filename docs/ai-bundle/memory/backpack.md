---
name: backpack
description: Our own Backpack (worn + animated, placeable, only strong blasts break it), PR #270 MERGED as pack 0.11.89
metadata:
  type: project
---
2026-10-15 Andy: "a backpack that animates on the player and can be placed on the ground. it is only broken by strong explosions". Traveler's Backpack has no 26.3 Fabric build, so own: `backpack/` + `client/backpack/`, PR #270 MERGED 2026-10-16 02:3xZ (branch claude/project-thread-nk7j3r, pack 0.11.89), jar /mnt/project-files/backpack/. Not playtested.
- Wear: right-click air; ` opens, sneak+` takes off. Placed: unmineable, IMMOVEABLE, wither/dragon immune; BackpackExplosionMixin breaks it at radius >= 6 (Backpack.STRONG_BLAST). Death sets it down where you fell.
- Rendering: shared ModelParts are never posed (submits are deferred); transforms go on the pose stack. Sway spring in BackpackClient.Sway.
