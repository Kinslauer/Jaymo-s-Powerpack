---
name: flight-terrain-ahead
description: Fast flight slows while chunks ahead load (client/TerrainAhead) + Chunky back, PR #165 MERGED pack 0.10.51 (2026-10-14)
metadata:
  type: project
---
2026-10-14: Andy got stuck on invisible blocks flying faster than chunks load and missed Distant Horizons. PR #165
(pack 0.10.51): client/TerrainAhead + TerrainAheadMixin (Entity.move) slows horizontal flight above 14 b/s when the chunk
1 s ahead isn't loaded or built (Sodium isSectionReady); Chunky 1.5.3 re-added for Deck pre-gen (`chunky radius 2500`).
DH 3.3.4 exists for 26.3 and works with Iris, but it hung the Deck server, so it stays off the server; a decision card
asked whether to re-add it client-only (Andy chose: leave it out). C2ME 26.3 is alpha, not added.
Related: [[distant-horizons-chunky]]
2026-10-15: Bobby 5.2.16 added (PR #242 merged, pack 0.11.50, CF 409301/9077335, dev:false). C2ME 26.3 still alpha (0.4.2-alpha), skipped; revisit at beta.
