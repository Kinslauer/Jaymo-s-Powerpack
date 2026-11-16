# Server no longer freezes reading land that isn't made yet (crash-risk sweep)
- **Fix (watchdog hangs)**: a sweep of every server file that reads the world found more places that read a chunk
  still being made (or not loaded at all), which holds the server thread until it generates. On the Deck that is
  how the server kept running past the 60 s watchdog. They now check `Loaded.made` first and skip, retry later, or
  end the move:
  - `Stacked.surface` (the height hook every ground lookup in a stacked world goes through): one of the Nov 13 crashes.
  - `SkyfallCleanup`: `hasChunk` had come back into its queue check.
  - Outpost campfire respawn: campfires are tried nearest first, and at most 3 far ones are read. It used to read all
    32 on one death.
  - Lab escape and android starts (`WildRoom`): at most 4 tries on new land, not 28.
  - Also `LimbEaters`, `SunExposure`, `ElementalCorruption`, `Hiding`, `Ledge`, `ArsenalEmber` (a rift to land that
    isn't made loads it and lets you through on a later pass), Ember fireballs and Avatar balls, `GravityPush` (the
    push ends at land that isn't made), `BlindnessService`, `ResonanceFate`, `WindModes`, `WindGift`, `Lumen`,
    `Thornwild`, `SmokeCloudshaper`, `WebCracks` and the Future's arrival spot.
  - Lights, leaves and cracks on land that isn't made are kept and cleared later, never dropped.
- **Fix (crash)**: Darktide waves no longer throw when a lethal hit cancels a wave mid-tick, and web takedowns skip a
  thrown target that's gone.
- Built; not tested in game.
