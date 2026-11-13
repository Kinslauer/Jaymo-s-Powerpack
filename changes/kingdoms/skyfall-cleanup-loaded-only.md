# Checks for loaded land no longer wait for land being made
- **Fix (possible watchdog crash)**: `level.hasChunk` in 26.3 is also true for a chunk still being generated, so the
  reads it guarded could wait on the server thread (the Deck crash at 2026-11-13 11:57 came from the same guard in
  Powers). Every server-side guard now uses `getChunkSource().getChunkNow(...)`, true only for land that's made and
  loaded: `ArrivalGround`, `Labyrinths`, `BogLights`, `Haunts`, `Debris`, `Groundwork`, `QuestFoes`, `Roads`,
  `Seats`, `Shelter`, `Weathering`.
- Built; tested on the Deck server.
