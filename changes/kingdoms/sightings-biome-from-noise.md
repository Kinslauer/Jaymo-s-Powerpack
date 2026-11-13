# Rumours of beasts no longer load land to read its biome
- **Fix (watchdog crash)**: a rumour of a monster of legend (`Sightings.rumour`) picks a spot hundreds of blocks out
  from a settlement and read its biome with `level.getBiome`, which waited on the server thread for a chunk not loaded
  or still being made (Deck crash 2026-11-13 14:23, 58 s on chunk [20, -9]). Loaded land is read as before; elsewhere
  the world's biome source answers without touching a chunk.
- Built; tested on the Deck server.
