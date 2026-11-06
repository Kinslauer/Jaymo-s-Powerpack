---
name: rocks-match-ground
description: PR #817 (pack 0.19.22, merged 2026-11-01): loose rocks take their stone from the block they lie on; grass always plain cobblestone rock; gravel half flint
metadata:
  type: project
  modified: 2026-11-01T01:11:57.661Z
---
2026-11-01 Andy: "way too many sandstone rocks [...] primarily flint and cobble stone on this gravel plain, the rocks should adapt to whats underneath it, except for grass, then it always does cobblestone rock pebbles".
PR #817 merged as pack 0.19.22 (ground/GroundRocksFeature.surfaceRock, PebblePilesFeature).
- Block underneath decides: grass/gravel/stone/calcite -> STONE rock (the "cobblestone" one); sand/sandstone -> sandstone; red sand/terracotta -> red sandstone; granite etc. -> own.
- Dirt-like ground still checks the column below but ignores buried sand. Biome sandstone fallbacks removed.
- Rocks on gravel are loose flint 45% (GRAVEL_FLINT). Pebble piles on grass always STONE (biome pebble rocks from 0.17.175 removed).
**How to apply:** don't reintroduce biome-based rock picks on grass. New chunks only. See [[ground-rocks-rlcraft-start]].
