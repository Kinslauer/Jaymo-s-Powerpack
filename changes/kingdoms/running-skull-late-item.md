# The server starts again (running skull's look)
- **Fix (server would not start)**: the Laughing Bones' running skull made its skeleton-skull look as its class
  loaded, during registration, before items are ready, so Kingdoms failed to start (Deck, 2026-11-14 7:41 AM). The
  look is now made the first time it is drawn.
- Built; tested on the Deck server.
