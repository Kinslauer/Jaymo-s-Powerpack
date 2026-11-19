# Crawling into a wall sits you up

- Downed players who crawl into a wall now sit up against it, the same as pressing the Sit key beside one. Pushing at the wall for about a third of a second starts the get-up. If you use the Sit key to lie back down while you're still pushing, it won't sit you up again until you let go.
- `DownedLie#crawlInto` (called from `Downed#tickPlayer`) reads the player's movement keys (`getLastClientInput`), turns them by the look, and starts `prop(player, true)` after `BUMP_TICKS` (6) ticks pushing within `BUMP_GAP` of a wall's face, at least `BUMP_FACING` (0.7) of the push straight at it. Passed out, carried, rising or already sat: nothing.
- Built; not played.
