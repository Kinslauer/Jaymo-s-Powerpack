---
name: chrono-time-stop-glyphs
description: Chrono Time Flow time stop (Space after Space counter) glyph burst + doubled length, PR #265 merged as pack 0.11.78, 2026-10-15
metadata:
  type: project
---
2026-10-15 Andy: "add a large glyph particle explosion to the time flow time stop on spacebar, it also only lasts 2s". It actually lasted 3-6 s (StasisField.duration by abilities unlocked). Took it as "too short": ChronoFlow.TIME_STOP_SCALE = 2 (6-12 s); G's Stasis Field unchanged. Burst = ChronoFlow.glyphBurst, 320 ENCHANT particles out to 7 blocks (sent at end point, motion back to centre: FlyTowardsPositionParticle flies from pos+motion to pos). Andy said "merge" without picking another length. PR #265 MERGED 16:4xZ as pack 0.11.78; PR #267 (future zombies) also held 0.11.78 and must take 0.11.79. Not playtested.
