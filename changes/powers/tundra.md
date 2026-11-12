# the tundra's blocks, plants and living lichen
From Klei_Wright's "Tundra game drop" concept (`docs/Tundra-Plan.md`, track A). Package `com.jaymospowers.tundra`;
every texture is painted from nothing by `tools/gen_tundra.py`, the models are our own.
- **Banded Iron Ore**: iron in smooth basalt, in pale bands. Stone pick or better; 2 to 4 raw iron (plain ore gives 1),
  Fortune as on any ore, silk touch keeps the block. Smelts and blasts into an iron ingot.
- **Dwarfed Spruce Log** (a 10-wide pillar) and **Dwarfed Spruce Post** (6 wide): placed along any axis like a log,
  stand in water, an axe strips them. 2 logs or 4 posts craft into 1 spruce log (the stripped ones into a stripped
  spruce log). They burn, and count as logs (`minecraft:logs_that_burn`).
- **Stunted Red Shrub, Orange Shrub, Yellow Shrub**: small bushes on dirt, grass, moss, yellow moss and gravel. Shears
  or silk touch to keep one.
- **Yellow Moss** and **Yellow Moss Carpet** (2 moss -> 3 carpets); hoe-mined like moss, and plants root in it.
- **White Ice**: opaque white ice that never melts, as slippery as ice. A pickaxe gets it back.
- **Sprouted Glow Lichen**: bone meal on vanilla glow lichen sprouts it (same faces). By day, with sky light 9 or more,
  it breaks the rock it clings to down a step (stone, deepslate, basalt, smooth basalt, granite, diorite, andesite,
  tuff, calcite, cobblestone, cobbled deepslate, sandstone -> gravel; gravel -> sand 60% / coarse dirt 40%; coarse
  dirt -> dirt), or seeds a plain glow lichen onto another such block within 8. With no rock left in reach it stops;
  with 12 lichen within 8 it stops seeding. Never ores, never a block that holds something. `LichenLife`; the rock
  lists are the block tags `jaymospowers:lichen/to_gravel`, `to_soil`, `to_dirt`.
- **Shears** on a sprouted lichen snip it (it only glows from then on); on vanilla glow lichen they make **Snipped
  Glow Lichen**, a lichen that never spreads. Builds are safe.
- **Succession**: yellow moss creeps onto dirt and coarse dirt beside it while glow lichen is within 4 blocks; under
  open sky it sometimes raises a shrub (stunted red where rock is still under the moss, else orange or yellow), no
  more than 3 within 5 blocks. `YellowMossBlock`.
- **Phosphor**: the glow ink sac is renamed Phosphor (English) and redrawn; any glow lichen smelts into one.
- **Glow lichen's look**: vanilla glow lichen (and ours) grows tufts and crusts on a floor and hanging strands under
  a ceiling.
- Changed on purpose: bone meal in the hand no longer spreads vanilla glow lichen, it sprouts it (a dispenser still
  spreads it).
- Not built: yellow moss layers through the saw (the layer list is generated from wooden and stone blocks, not a
  one-line registration); a claims check for the lichen (Powers has none to ask).
- Built and compiled; not played. JEI showing the Phosphor recipe is unchecked.
