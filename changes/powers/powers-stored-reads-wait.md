# stored reads wait for the land (Deck watchdog crash)
- **Floods, river ice floes, Arsenal portals**: they no longer read blocks in land that isn't made and loaded yet. A flood waits a step if the land around its gap isn't there, a floe stays tracked and drifts when its land (or the water ahead) comes back, and a portal pass waits until the far exit's land is loaded. Nothing is dropped; nobody sees a change except no more server hangs.
- Classes: `Floods`, `RiverIce`, `ArsenalStanding`, all through `core.Loaded`.
- Built; tested on the Deck server.
