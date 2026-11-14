# Blossom only on Mohknee; no kingdom raised out at sea
- **Blossom only on Mohknee** (Andy, of a Blossom-style castle at his farmhand start: "these bloosom castles should
  only spawn on mohknee no more blossom anywhere else"). A cherry grove on the home continent is plain country now
  (`Palette.of`): a kingdom or village founded there gets no Blossom castle (the grand seats' temple and dojo), no
  minka, no ronin harness and no Blossom tongue. Everything on Mohknee is Blossom as before (`Palette.at`).
  Home-continent settlements already standing in a cherry grove keep their buildings but count as plain country from
  the next start.
- **No kingdom raised at sea or on an island.** The kingdom a life story raises when the world has none
  (`StoryGround.citadel`) is moved to the nearest mainland within 2,400 blocks when the place asked for is under the
  sea or on an island (`StoryGround.mainland`, `Offshore.island`), and the coastal capital's walk in from the sea
  passes over islands (`StoryGround.shore`).
- **On Mohknee, by the water, on the mainland.** A Blossom village or town rises only with water (sea, river or lake)
  within about 150 blocks and never on an island off the shore (`BlossomVillages.open` / `waterNear`). Andy: "these
  should always spawn near water, but they need to be on a large landmass."
- All read from the ground's shape, so no chunks are loaded. `/kingdom found blossom` and `blossomtown` still build
  where the operator stands. Compile-checked; not seen in game.
