---
name: web-slide
description: Web parkour: 4 random vault looks + 4 random slide looks, slide jump-out leaps higher/further; PR #215 (pack 0.11.19)
metadata:
  type: project
---
2026-10-15 Andy: slide pose "pretty awful"; first asked for legs flat forward + hand high behind, then scratched that for 4 new variations each of sliding and vaulting. Also a jump out of a slide with more height and momentum (kept), and it always flips (front flip/corkscrew/cartwheel, WebParkourClient.SLIDE_FLIPS).
PR #215 MERGED 2026-10-15 03:27Z (pack 0.11.19), branch claude/project-thread-vb1xbx, jar /mnt/project-files/parkour-variants/. Not playtested.
Vaults: kong, lazy, monkey flip, thief. Slides: baseball, knee, hip, head-first. Server picks at random, never the same twice running; synced in WebParkour.MOVE as (variant*8+kind)*8+face+1.
**How to apply:** pose knobs at the top of WebParkourPose; jump in WebParkour.SLIDE_JUMP_* (up 0.62, speed x1.3 + 0.12, cap 0.95). Related: [[web-swing-cling]].
