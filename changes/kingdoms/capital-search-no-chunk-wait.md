# The first capital's village search no longer stalls the server
- **Fix**: on a new world, the first newcomer's join starts the search for the village the first capital rises in.
  Where vanilla's check said a village could start, the search made that chunk on the server thread and waited for it.
  During a Chunky pregen that wait sat behind the pregen's queue, and on the Steam Deck it ran into the 60 s watchdog
  (8:15 PM, 2026-11-15). The chunk is now made in the background and looked at on a later tick, so the server keeps
  ticking while it's made. The search still only takes a village that really started there.
- `StoryGround.VillageSearch` (`step`, `started`).
- Built; not played. A join on a fresh world wasn't tested.
