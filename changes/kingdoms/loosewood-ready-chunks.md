# loose wood no longer makes the server wait on land still generating
- **Fix**: falling-wood checks no longer stop the server while new land generates near a player (two watchdog crashes on 2026-11-21, 8:10 and 8:16 AM). It only reads blocks in chunks that are fully there; the rest wait for a later tick.
- Class: `LooseWood` (`ready`, in place of `Level.isLoaded`).
- Built by CI; not played.
