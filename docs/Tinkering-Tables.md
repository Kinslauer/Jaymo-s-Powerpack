# Tinkering: the extra tables (Tool Forge, Crafting Station, Pattern and Part Chests)

Andy: "our tinkers mod is missing features like the pattern chest and tool forge". Ported from Tinkers' Construct (MIT).
Code in `tinkering/table/` (`Tables` registers it all, called from `Tinkering.init` before the station block entity),
client in `client/tinkering/` (`TableUi`, `CraftingStationScreen`; the Tool Forge reuses `ToolStationScreen`).
Assets and data: `tools/tinkering_tables_assets.py <TC resources dir>` (separate from `tinkering_assets.py`; it merges into
`mineable/axe.json` and `pickaxe.json`, which `tinkering_assets.py` writes outright, so run it after that one).
Lang: `lang/parts/tinkering_tables.json`.

## The blocks
| Block | What it is | Recipe |
|---|---|---|
| `tool_forge` | Tinkers' anvil, 5 part slots + result; everything the Tool Station does | 3 blocks of metal (tag `jaymospowers:forge_metals`: iron, copper, steel, silver) over a Tool Station over 3 Seared Bricks (blocks): `mmm / _t_ / sss` |
| `crafting_station` | 3x3 crafting table with vanilla's recipes; the grid stays on it | shapeless: Crafting Table + Pattern + any planks |
| `pattern_chest` | 54 slots, only Patterns and Casts (`CastItem`) | Tinkers' cast chest: ` p ` / `bCb` / `bBb` (p pattern, b seared brick, C chest, B seared bricks) |
| `part_chest` | 54 slots, only tool parts and weapon heads | Tinkers': ` p ` / `sCs` / `sws` (pattern, sticks, chest, planks) |

The Crafting Station recipe is shapeless with a third ingredient because "Pattern over Crafting Table" is also the Tool
Station's recipe (shapeless, 2 ingredients) and the two would clash.
Chests drop their contents when broken and face the player. Hoppers and shift-clicks respect the filters
(`TinkerChestEntity.canPlaceItem`). `ROWS` is the one number to change their size.

## More than 3 parts: the Tool Forge
`ToolStationMenu` takes a layout (4 slots for the Station, 5 for the Forge; `ToolStationMenu.forge(...)`, own MenuType
`Tables.TOOL_FORGE_MENU`). `Tables.STATION_MAX_PARTS = 3`. The rule is in the menu, generic from `type.parts.size()`:
a tool whose parts all match but number more than 3 gives no result at the Station and shows the red status line
"Needs a Tool Forge"; a weapon head counts as a part (head + the parts its kind takes). So the sledge hammer and cleaver
(4 parts) are made at the Forge, as is any 4-part tool track B adds. Repair, modifiers and part swapping work at both.
The Station's recipe list leaves the Forge-only tools out and ends with a hint. The Station keeps 4 slots (modifier
recipes use them).

## Side inventory and tabs (all four tables)
Both live in `TableExtras`, held by every table menu (`TinkerTableMenu`).
- **Side inventory:** the first container beside the table (the 4 horizontal neighbours; a Pattern or Part Chest first,
  else any container block entity, double chests included) shows in a panel at the left of the screen; click and
  shift-click work (from the player's inventory: the table first, then the side container; from the side container: the
  table if it takes it, else the player). The slots always exist (54 per menu) and only as many as the container has are
  active, with the count sent in a data slot, so no extra open data. When the panel shows, the info panel (recipes,
  stats) moves to the right of the screen. The menu closes if the container goes away.
- **Tabs:** along the top, one per Tinkers table (Part Builder, Tool Station, Tool Forge, Crafting Station) touching
  this one, in that order; only when there are two or more. Click to hop without closing: a menu button (1000 + the
  kind) the server answers by opening that table's menu (busy tables say so, as when walking up to them).

## Table storage
`StationEntity.SIZE` is 9 (the Crafting Station's grid); old saves of 4 load into the first slots. The renderer lays the
items on top (the Forge's five along its top, the grid in 3x3). `StationEntity.busyFor` recognises the new menus.
`StationEntity.open/changed/close` are public now.

## Smeltery extras (melter, tank, channel)

After Tinkers' Construct 3 (MIT; its models and textures). All in `tinkering/smeltery/`, registered by `SmelteryExtras`.
Anything that gives or takes metal is a `MoltenHolder` (smeltery, melter, tank, channel, casting table and basin); a faucet
pours from the holder it hangs on into the holder under it. `MoltenEntity` is the shared block entity (its `Tank`, saving,
sync every 5 ticks); `MoltenRenderer` draws the metal.

| Block | What it does | Recipe (b seared brick, g glass) |
| --- | --- | --- |
| `seared_melter` | A one-block smeltery. Set it over heat (what the kiln counts: campfire, fire, lava, magma, a lit forge hearth; `kiln.Heat`). Right-click with up to 3 things that melt; empty hand takes back what hasn't melted, then reads the pool. Holds 12 ingots, does not alloy. A faucet on its side pours it. | `bgb` / `bbb` |
| `seared_tank` | Holds 4 blocks of one metal. A faucet or channel over it fills it, a faucet on its side empties it; empty hand reads it. Breaking it loses the metal. | `bbb` / `bgb` / `bbb` |
| `seared_channel` | A trough: joins to channels beside it, opens downward over a casting table, basin, tank or channel. A faucet above pours in; each stretch fills what is under it first, then passes metal on. Dregs under a nugget that can't move cool away after 10 s. | `b b` / `bbb` (3) |

## Not done / notes
- Tool Forge has no facing (its top runs north-south); JEI isn't told the Forge also makes tools.
- JEI: the Crafting Station is a crafting catalyst and takes a recipe's "+" (`TinkeringJei.registerRecipeTransferHandlers`; the
  grid is filled from the player's inventory, not the side inventory). No vanilla recipe book.
