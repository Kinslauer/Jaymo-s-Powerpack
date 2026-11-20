# A leg or bagged corpse of a modded creature no longer crashes the game
- **Fix**: drawing a severed leg (`SpiderLeg`) whose creature another mod adds saved data to (Re:Animal's wolves) crashed the game: that mod's part of the save isn't set up on a body rebuilt only to be drawn. The leg now keeps what was read before it (as corpses already did), and a corpse taken out of a body bag does the same (`CorpseEntity.rebuildFrom`). A warning is logged instead.
- Built by CI; not played.
