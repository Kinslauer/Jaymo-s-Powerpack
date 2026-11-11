# cobalt ore and the knife metals - the four ingots nobody could get
- **Why**: Andy: "All items need crafting routes that make sense". Cobalt, Manyullyn, Queen's Slime and Hepatizon ingots
  had knives, stats and traits but no ore, recipe, loot or trade: survival could not get one.
- **Cobalt Ore**: blue spots in the Nether's netherrack (`Tinkering.COBALT_ORE`, feature `ore_cobalt`: veins of 5, 4 a
  chunk, y 8 to 118). Iron pickaxe; drops itself. Smelts or blasts to a Cobalt Ingot, or melts in the smeltery for two.
- **Alloys** (`Melting.ALLOYS`, cast into ingots on a casting table):
  - Manyullyn: 3 cobalt + 1 steel makes 4.
  - Queen's Slime: 1 cobalt + 1 gold + slime makes 2.
  - Hepatizon: 2 copper + 1 cobalt + amethyst makes 2.
- Four new liquids (`Molten.COBALT`, `MANYULLYN`, `QUEEN_SLIME`, `HEPATIZON`): ingots only, no cast parts, since their
  parts are knife parts made from the ingot at the Part Builder.
- Assets from `tools/gen_knife_metals.py` (the ore is vanilla's nether gold ore with its spots recoloured).
- Tinkers' own Manyullyn takes ancient debris and its Queen's Slime magma cream; ours use steel and slime so no new
  liquid is needed. Change the lines in `Melting.ALLOYS` to retune.
- Lore: a `(new)` Cobalt paragraph under "The Nether and the demons" for Andy to approve.
- Built (Gradle compileClientJava clean); not played, the ore's spawn rate is a first guess.
