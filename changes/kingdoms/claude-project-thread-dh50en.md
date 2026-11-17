# capital village search and ford keep site off the server thread
- **Capital village search**: right after the first player joined, the search for the capital's village placed
  structures on the server thread (about 8 s of loading and upgrading templates, then more), so the game froze with
  "can't keep up" warnings. The spot checks now run on a background thread in order, using the worldgen-safe
  `Structure.findValidGenerationPoint` path, and the server only follows their answers each tick (`StoryGround.VillageSearch`).
  Same spots, same order, same village picked.
- **Keep above the ford**: its site search (about 4 s) runs on a background thread too (`Ford.planning`), then sets the
  plan or gives up as before.
- Built and timed on a dev server; not played.
