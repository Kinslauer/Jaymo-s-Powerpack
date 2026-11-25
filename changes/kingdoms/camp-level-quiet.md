# Building a camp or village at a chunk's edge no longer freezes the server

- Levelling the ground for a camp, Blossom village, farmstead, watch post or castle (`CampBuildings.level`) set each block with neighbour shape updates. At the circle's edge those read the block next door, and when that was in a chunk not loaded yet the server thread made the chunk; the server stopped ticking and the watchdog crashed it (Deck, 2026-11-25). The levelling now skips neighbour shape updates (`CampBuildings.QUIET`, as the goblin buildings and mines already do).
- Built by CI; not played.
