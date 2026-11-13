# Server froze reading land still being made (Deck crash 11:57)
- **Fix (watchdog crash)**: `level.hasChunk` in 26.3 is true for a chunk that's only scheduled (its holder at FULL
  ticket level) and not made yet, so reading it waited on the server thread for it to generate. Right after a join
  `SkyfallCleanup`'s sweep (21x21 chunks round each player) read such a chunk and held the server 60 s (Deck crash
  2026-11-13 11:57). Every server-side `hasChunk` guard now uses `getChunkSource().getChunkNow(...)`, which only
  answers for a chunk that is made and loaded: `SkyfallCleanup` (also the blocks beside a spring), `WildRoom`,
  `Mortality`, `GravityMeteorSweep`, `ZFighterAfterlife`. `SkySpringFeature` (world generation) is unchanged.
- Built; tested on the Deck server.
