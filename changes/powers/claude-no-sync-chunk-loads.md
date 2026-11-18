# Server stops waiting on land that is not made yet

- Fixes: the Deck server froze for 22, 31 and 41 s (2026-11-18 10:54, 11:00 and 11:05 AM; the last dropped a player) with the server thread inside `ServerChunkCache.getChunkBlocking`, waiting for chunks that were only scheduled. About 2,200 profile samples, by caller: Immersive Snow Reloaded setting snow beside an unloaded chunk (about 450, the largest), natural spawning (about 330: `PortalBands.surface`, and the biome and structure reads of `getRandomSpawnMobAt`), the running-water freeze check of our Dynamic Waters port (about 70), Advanced Chimneys' block search (about 100).
- Immersive Snow Reloaded's `Logic.processChunk` runs only when its chunk and all eight around it are made and loaded (`SnowProcessChunkMixin`, skipped without the mod).
- ForgeEndertech's `GameWorld.isBlockLoaded`, which Advanced Chimneys' searches ask before stepping onto a block, now answers true only for a made and loaded chunk (`WorldLoadedMixin`, skipped without the mod).
- Natural spawning gives up an attempt whose chunk, or whose spot (`getRandomSpawnMobAt`), isn't made and loaded (`SpawnLoadedMixin`).
- Dynamic Waters port: `higherWaterBeside` and the ice floe spawner ask `Loaded.made` instead of `hasChunkAt`.
- Not touched, still in the profiles: entities reading fluid beside them (`Entity.updateFluidInteraction`), MCA villager point-of-interest and bed checks, and biome attribute sampling (`EnvironmentAttributeSystem`), about 250 samples together.
- Built by CI; not played.
