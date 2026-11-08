---
name: small-spiders-ai
description: Andy's More Babies-sized spiders + NYF/Yuka AI ask (2026-10-20): Kingdoms wild/SpiderMinds, PR #329
metadata:
  type: project
  modified: 2026-10-20T16:36:47.342Z
---
2026-10-20 Andy: "make every spider this size (More Babies) then give them this ai (NYF's Spiders, Yuka)".
- More Babies is ARR: only its baby proportions used (spider 0.5, cave spider 0.65 via a permanent scale modifier `jaymoskingdoms:spider_size`). Tarantula keeps its size.
- NYF's Spiders (LGPL) has no 26.3 build; Stormie's Spiders (already in pack) IS its fork, so climbing AI was already in.
- Yuka (MIT, JS) = ideas only: kingdoms wild/SpiderMinds adds 6 goals on load (perceive/hearing+memory, evade p2, pursuit p3, search p4, ceiling ambush p4, wander p5).
- Kitchen Corpses.look now keeps the scale attribute; CorpseEntity.rebuild applies attributes on the client (vanilla reads them server-only).
- PR #329 MERGED 2026-10-20 17:2xZ as pack 0.13.35 / Kingdoms 0.7.97 / Kitchen 0.4.50; not playtested. Next free: 0.13.36 / 0.7.98 / Kitchen 0.4.51. Related: [[immersion-mods-0152]].
