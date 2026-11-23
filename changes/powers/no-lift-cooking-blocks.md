# Sneak-clicking a cooking block opens it instead of lifting it
- **Carry On no longer lifts cooking blocks** (Andy: shift-clicking picked up every cooking block, campfires too, so no
  cooking screen could be opened). Added to `#jaymospowers:carry_on_no_lift`: campfires (`#minecraft:campfires`),
  furnace, smoker, blast furnace, Cooking for Blockheads' ovens, counters (their top is a prep surface), cooking tables
  and cutting board, and Kitchen's stove, cutting board, cooking pot, frying pan, spit, great spit, hearth, brewing
  keg, cask, still, tea kettle and sushi mat. Carry On passes a refused lift on, so sneak + right-click there opens Kitchen's cooking screen
  or does the block's own use. These blocks can still be mined and placed as before; only Carry On's lift is gone.
- Data only (the tag); `docs/carry.md` updated. Built in CI; not played.
