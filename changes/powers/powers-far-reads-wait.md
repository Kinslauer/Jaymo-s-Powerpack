# far reads wait for their chunks
- **Server stalls**: the Karma descent no longer lands in Nether chunks that are not ready (after the wait the player's life ends as any other, and the wake spot now loads one chunk around it), the Wind rise to the Aether only reads made chunks beyond its arrival column, and Z-Fighter raider landings skip pad columns whose chunk is not made yet (they try again later) instead of making chunks on the server thread.
- Built; not played.
