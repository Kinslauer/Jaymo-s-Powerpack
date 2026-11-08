---
name: wagon-bench-stuck
description: 2026-11-05 PR #957 (Kitchen 0.5.62 + Kingdoms 0.7.544): wagon bench villager stuck/pitch cap/haul-up; Kingdoms Wagon carts sunk in terrain now stand on ground
metadata:
  type: project
  modified: 2026-11-05T00:02:48.005Z
---
2026-11-05 Andy: "this guy got stuck" (screenshot: quest villager up on a near-vertical Kitchen wagon, hillside). Diagnosis was inferred from code, not reproduced: CartEntity pitch was atan2(rise, pole), hitch snaps only at ~6 blocks, so a horse up a bank stood the cart on end; wagon/coach take in any peaceful mob that bumps them (like a boat) and kept it until sneak+right-click.
PR #957 (branch claude/project-thread-6mvzja, Kitchen 0.5.62 only, built, not playtested): MAX_PITCH 30, haul up 1 block when blocked and puller >1 higher, self-boarded non-tame mobs step down after the cart stands still 30 s (SIT_STILL_TICKS). Also Kingdoms 0.7.544 in #957: Andy "carts often spawn stuck inside terrain" (clip MedalTVMinecraft20261104190002959): Kingdoms Wagon was placed at horse Y with no terrain check (hitch/snap/trail); Wagon.ground picks the nearest open surface 3 up/4 down. MERGED 2026-11-05 00:09Z. Royal bodies left unburied went to a separate thread.
See [[kingdoms-caravans]], [[mob-unstick]].
