# The server starts again: the wish radar's parts were registered twice
- **Fix**: the server crashed on start ("Adding duplicate key ... jaymospowers:radar_part_1"). Two Z Fighter gaps PRs both
  added the wish radar's three parts, one in `ZFighterItems` and one in `ZFighterWishes`. They are registered once now,
  in `ZFighterItems`, and building the radar reads those.
- Class: `ZFighterWishes`.
- Built; the server was started with it.
