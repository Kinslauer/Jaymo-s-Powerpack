# MCA's village swap waits for its land (Deck crash 22:32)
- **Fix (watchdog crash)**: MCA swaps a new village's villagers and zombies for its own from a queue
  (`SpawnQueue`), one a tick, where the old one stood. With Chunky making land fast on the Steam Deck one stood in a
  chunk not loaded, and spawning there waited on the server thread a minute (crash 2026-11-13 22:32).
  `McaSpawnQueueMixin` sends one whose land isn't made and loaded to the back of its queue; if none is ready the tick
  waits. None is lost.
- Built; tested on the Deck server.
