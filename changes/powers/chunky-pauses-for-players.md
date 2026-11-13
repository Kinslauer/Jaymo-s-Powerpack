# Chunky pre-generation waits while anyone plays; the Deck's server gets 8 GB
- **Fix (out of memory)**: the Steam Deck's server ran out of its 6 GB heap during an autosave while Chunky
  pre-generated a new world's 2,500 blocks and Andy explored (crash 2026-11-13 18:22). Andy: raise it to 8 GB and pause
  Chunky while players are on. `ChunkyCompat` (only with Chunky installed) runs `chunky pause` when the first player
  is on and `chunky continue` once nobody is, and at start-up with nobody on. `deck/start.sh` now starts the server
  with `-Xmx8G`.
- Built; tested on the Deck server.
