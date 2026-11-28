# Sneak-click a liftable block for a choice box: "Use (bake)", "Lift"
- **A small box instead of a straight lift** (Andy: "a context box for blocks like these, with use (and the specific in
  parentheses, for example a furnace would say "Use(bake)") lift"). Sneak + right-click with empty hands on any block
  Carry On could lift (chests, barrels, furnaces, campfires, ovens, Kitchen's stations...) opens a box right of the
  crosshair: **Use (...)** does what the sneak-click did before (Kitchen's cooking screen, a chest opening), with the
  block's own word (furnace bake, campfire cook, smoker smoke, blast furnace smelt, oven bake, counter prep, cooking table
  cook, cutting board chop, stove cook, pot stew, pan fry, spit roast, hearth warm, keg brew, cask age, still distill,
  kettle brew tea, sushi mat roll; other inventories "open"); **Open door** on a Cooking for Blockheads oven; **Lift**
  lifts it with Carry On as before. Escape closes it.
- Cooking blocks can be lifted again (through Lift): they're out of `#jaymospowers:carry_on_no_lift`.
- `core/BlockChoice` (the packet; the server checks reach, the clicked spot, spawn protection and empty hands, replays
  Use as a sneak-click, calls Carry On for Lift), `client/BlockChoiceScreen` (the box and its click hook),
  `core/CarryOnCompat` (`couldLift`, `keyPressed`, `liftChosen`), `mixin/carryon/CarryOnPickupMixin` (Carry On lifts only
  from the box). Words in lang `screen.jaymospowers.block_choice.*`.
- Built in CI; not played.
