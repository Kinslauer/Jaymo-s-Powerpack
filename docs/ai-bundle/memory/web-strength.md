---
name: web-strength
description: Stronger Web (Andy picked all 7 ideas 2026-10-14): spider physique, impact/taser webs, web bomb, webbed fists on X; PR #146 MERGED, pack 0.10.21
metadata:
  type: project
  modified: 2026-10-14T07:47:31.362Z
---
2026-10-14: Andy asked for a stronger Web after linking Powerborne Heroes. That mod is a 1.20.1 Palladium addon with no licence listed, so it counts as ARR: we remade the ideas ourselves. Andy said "all" to the 7 ideas. PR #146 MERGED 2026-10-14 08:20Z (pack 0.10.21, branch claude/project-thread-ve4aqx); not playtested.
Most of it lives in `power/web/WebStrength.java` + `WebLivingEntityMixin` (pinned targets take 1.5x damage).
Keys: Sneak + tap V = web bomb; V while B holds a target = taser shock; X = Webbed Fists (Sneak + X when bonded to the symbiote, because plain X opens the symbiote menu). Sneak + Z was avoided: Sneak during Zip means "land on top".
**How to apply:** tune balance in the constants at the top of WebStrength/WebBall/WebPower/WebAscension.
