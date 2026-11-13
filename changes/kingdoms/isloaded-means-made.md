# Loaded-land checks wait for nothing (Deck crash 3:36 PM)
- **Fix (watchdog crashes)**: in 26.3 `Level.isLoaded` and `hasChunkAt` are also true for a chunk only scheduled and still
  being made, so the reads they guarded waited on the server thread for it to generate. On the Deck, a quicksand-pit
  roll beside a player just set down on Mohknee (all fresh land) waited 44 s on chunk [1748, 1] and the server shut
  down (2026-11-13 15:36). Every such guard now goes through `Loaded.made`: on the server, true only for land that is
  made and loaded (`getChunkNow`); on the client and in world generation, the game's own check as before.
- Built; tested on the Deck server.
