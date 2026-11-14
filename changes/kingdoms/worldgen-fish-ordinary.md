# Worldgen fish swim off like any other (Deck crash 22:41)
- **Fix (watchdog crash)**: William Wythers' Overhauled Overworld lays schools of tropical fish, cod and the like
  into its reefs, rivers and ponds as worldgen templates, kept for good, so they never left. A new world on the
  Steam Deck had 17,925 in the seven regions around spawn; 3,007 loaded when Andy joined and ticking them held the
  server a minute (crash 2026-11-13 22:41). `WorldgenFishMixin`: the first time such a fish is checked for
  despawning it stops being kept, so it leaves when no one is near and natural spawning brings fish back within its
  caps; one in a chunk that already has 6 fish goes at once. Fish from a bucket or with a name tag, and a Druid's fish form, are kept.
- Built; tested on the Deck server.
