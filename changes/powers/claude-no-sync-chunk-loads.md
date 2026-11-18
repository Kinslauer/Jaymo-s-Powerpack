# Server stops waiting on land that is not made yet

- Fixes: the Deck server froze for 22, 31 and 41 s (2026-11-18 10:54, 11:00 and 11:05 AM; the last dropped a player) with the server thread waiting in `getChunkBlocking` for chunks that were only scheduled. By profile samples inside that wait: Immersive Snow Reloaded setting snow beside an unloaded chunk (about 560, the largest), Advanced Chimneys' block search (about 110), the running-water freeze check and ice floes of our Dynamic Waters port (about 130, mostly under the snow).
- Immersive Snow Reloaded's `Logic.processChunk` runs only when its chunk and all eight around it are made and loaded (`SnowProcessChunkMixin`, skipped without the mod). A chunk at the edge of loaded land waits until its neighbours are in (it may get its snow up to 10 minutes later).
- ForgeEndertech's `GameWorld.isBlockLoaded`, which Advanced Chimneys' searches ask before stepping onto a block, now answers true only for a made and loaded chunk (`WorldLoadedMixin`, skipped without the mod).
- Dynamic Waters port: `higherWaterBeside` and the ice floe spawner ask `Loaded.made` instead of `hasChunkAt`.
- Still in the profiles: MCA villagers' point-of-interest and bed checks (about 50 samples inside the wait).
- Not played; the port is compiled by the pack build.
