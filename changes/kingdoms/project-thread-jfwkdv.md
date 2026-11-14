# Blossom villages and towns rise by the water, on the mainland
- A Blossom village or town (the castle town included) now rises only where there is water near it (sea, river or lake
  within about 150 blocks of its middle) and never on an island: a site with sea all the way round it within 400
  blocks is passed over (`Offshore.island`). Andy, of a castle town standing out in the sea: "these should always spawn
  near water, but they need to be on a large landmass."
- `BlossomVillages.open` / `waterNear`; read from the ground's shape, so no chunks are loaded for it.
  `/kingdom found blossom` and `blossomtown` still build wherever the operator stands.
- Villages already standing are not moved. Compile-checked; not seen in game.
