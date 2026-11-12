# Fix: the game crashed on startup with the gun mod (tundra biomes)

- (Jung) The game crashed while starting ("No value registered for key jaymospowers:tundra" and
  "jaymospowers:lichen_woodland", from TaCZ's entrypoint). The tundra's two biomes were put into the Overworld's biome
  list from the moment the game loaded, and TaCZ builds a vanilla-only set of registries during startup, where those
  two biomes don't exist yet.
- The Overworld's list now stays vanilla's until every mod has started (`TundraWorld.place`, called from the server
  and client entrypoints), the same way the demon realm's Nether biomes already do. Worlds load their registries
  later, from the data files, so the tundra still generates in new chunks.
- The new ice caves biome had the same fault ("No value registered for key jaymospowers:ice_caves" would have been next)
  and waits for `IceCaves.place` the same way.
