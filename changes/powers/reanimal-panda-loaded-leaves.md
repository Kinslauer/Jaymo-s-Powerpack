# A red panda loading beside unloaded land no longer freezes the server (Deck crash 22:06)
- **Fix (watchdog crash)**: Re:Animal's red panda looks for leaves block by block all round it, even while it's being
  loaded from a saved chunk; next to the edge of the loaded land that read a chunk not loaded or still being made and
  held the server thread 61 s (Steam Deck, new world with Chunky pre-generating, 2026-11-13 22:06). A compat mixin
  (`RedPandaLeavesMixin`, only with Re:Animal installed, its code untouched) says "not in leaves" while the box reaches
  into land not loaded; the panda finds its leaves again once it moves.
- Built; tested on the Deck server.
