# Ice Caves, crevasses and brittle ice from blue ice (26.4)
- **Ice Caves** (`jaymospowers:ice_caves`): a new cave biome under the colder lands (temperature below -0.3), from about
  25 blocks under the surface down to the deepslate depths, like the lush and dripstone caves. The driest cold ground
  (the tundra and dry snowy plains) keeps its old rock. Where a lush, dripstone or sulfur cave would be, that cave wins.
  New chunks only.
  - The rock is packed ice with bands of calcite (material rule `jaymospowers:ice_caves`, noise
    `jaymospowers:ice_cave_gradient`). Large icicles of packed ice hang from the ceilings and rise from the floors,
    sometimes joined into columns. Icicles hang in patches (a few standing under them), snow and ice crystals lie on the
    floors, and there is no glow lichen. No lava lakes or lava springs; water springs, geodes and dungeons stay.
  - Ores come out in the ice inside a pocket of stone (deepslate below Y 0): every ore feature (ours, vanilla's and
    other mods', `OreStandInMixin`, `ScatteredOreStandInMixin`), Powers' great veins (`BigVeinFeature`) and Kingdoms'
    world tier veins (`GroundChanges`).
  - Spawns: the usual cave monsters plus strays (they spawn here without sky, `StraySpawnMixin`) and Frostbites.
    Mineshafts, trial chambers, ruined portals and strongholds can generate here. Frozen peaks music, icy fog.
- **Crevasses** (`jaymospowers:crevasse`, the 26.4 feature's settings): winding cracks carved into ice cave floors,
  narrowing at their ends and towards their bottoms, with a bed of powder snow down in them and roofed at floor level
  with brittle ice. They are only carved where the walls hold, so they never break into another cave.
- **Brittle ice from blue ice**: water with blue ice beside it (north, south, east or west) freezes into brittle ice
  when it is placed, flows or something next to it changes (`BlueIceWaterMixin`).
- Brittle ice: what may cross it is now the entity tag `#jaymospowers:does_not_break_brittle_ice` (Frostbites, strays).
- Classes: `frost/IceCaves`, `frost/IceCaveFeatures`, `mixin/icecaves/*`.
