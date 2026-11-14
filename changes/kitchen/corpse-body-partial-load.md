# A corpse whose body won't fully load still draws
- **Fix (client crash)**: a corpse draws its body by rebuilding the dead mob from its saved data. Re:Animal saves a
  wolf's ball-play but never makes it on the client, so reading it threw and the game crashed when a wolf's corpse
  came into view (Andy, drifting as a ghost after dying, 2026-11-14 3:35 AM). A body that only partly loads now
  keeps what was read and is drawn; the rest is logged.
- Built.
