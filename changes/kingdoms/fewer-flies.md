# Flies only at bodies rotting 3 minutes, or at piles of the dead
- **Flies** (Andy: "they should only come around bodies that are decaying for over 3m, and to piles of over 6 bodies"): a fly now comes to a body only once it has lain rotting 3 minutes (3600 ticks) by Kitchen's rot clock, which is saved with the body and stands still while it is kept fresh, hung or frozen, or to a body in a pile: more than 6 bodies within 4 blocks across (2 up and down), fresh or not. Tiny bodies (a dead fly or mouse) don't make a pile. Before, the wait went by time since death, which a body forgets on a reload, so any body seen after a chunk reload drew flies at once.
- Maggots, their bait, rot and the bearers are unchanged (maggots still come 10 minutes after death, as before).
- `SmallLife.bodies`/`pile`, `KitchenCompat.decayTicks` (falls back to time since death on an older Kitchen).
- Built; not played.
