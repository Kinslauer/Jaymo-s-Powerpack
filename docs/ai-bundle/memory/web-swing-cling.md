---
name: web-swing-cling
description: Legends Superheroes ask (2026-10-14): mod unusable (Forge 1.20.1, ARR); own active web swing + fists/suit-gated wall cling, PR #194 (pack 0.10.100)
metadata:
  type: project
  modified: 2026-10-14T23:26:31.147Z
---
Andy linked Legends: Superheroes Unlimited 2026-10-14 for its wall cling and swing animations. It is Forge 1.18.2/1.20.1, All Rights Reserved, and needs Legends Core + AzureLib, so it can't be used.
Andy's rulings: active swing only (legs tuck at the bottom of the arc and kick on the upswing, free arm flares, bank into turns). Wall cling only with webbed fists at first; in the symbiote suit the tendrils climb and cling instead.
PR #194 MERGED 2026-10-14 23:51Z (pack 0.10.100; branch claude/project-thread-f3nljv); not playtested. Next free: 0.10.101 is held by PR #195 (Spike Burst), so 0.10.102. Jar: /mnt/project-files/web-swing-cling/.
**How to apply:** swing knobs are in the "active swing" block of WebArms. WebStrength.FISTS is synced; WebParkour.canCling gates the cling. A suit cling makes WebTendrilWalk.climbing true. Fists last 10 s (FISTS_TICKS) with a 30 s cooldown, so cling is short-lived without the suit.
Also in #194: Andy found tendrils too stiff (asked about Mo' Bends: no, it only bends vanilla limbs and has no 26.3 port). Tendrils now stream back/ripple/slither on the move (WebTendrilLayer.DRAG_*); only planted walkers stay rigid.
Related: [[web-lines-weight]], [[web-strength]].
