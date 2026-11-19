# Server starts again: SWAY and Interactive Foliage stay on players' games

- Fixes: the server would not start after Interactive Foliage and SWAY joined the pack: SWAY carries client-only drawing code the server doesn't have. Both are now left out of the server's mods; players keep them (they add no blocks, items or network messages, so joining is unaffected).
- Built by CI; checked by starting the Deck server.
