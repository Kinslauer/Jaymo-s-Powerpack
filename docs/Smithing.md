# Smithing (after Overgeared)

Overgeared (phuccom000, MIT; notice in `tools/overgeared_license.txt`) rewritten into our Tinkering for 26.3 Fabric.
Code: `tinkering/smithing/` (server) and `client/tinkering/smithing/`. Tuning numbers are the constants at the top of
`Grade`, `HotMetal` and `Forging`.

## Heat and tongs
- Lay ingots (iron, gold, copper, silver, steel and the alloys) or a forging blank in a Forge Hearth's coals (right-click
  it). After `HotMetal.HEAT_TICKS` (100 ticks at heat 2; bellows halve it) they are hot. Take them with tongs or an
  empty hand; they stay hot while they lie in a lit hearth.
- Hot metal goes cold `HotMetal.COOL_TICKS` (60 s) after it leaves the fire: a Hot Ingot turns back into its ingot.
- Carrying anything hot with no tongs in either hand sets you alight. With tongs, they lose 1 durability every 2 s.
  Fire Resistance, creative and Emberborn (bare hands) are spared.
- Tongs: wooden (crafted: planks and sticks), iron and steel (forged: Tongs Jaws at the Tool Station).

## The Smithing Anvil
- Sneak-use a smithing hammer on a vanilla anvil to turn it into a Smithing Anvil.
- Lay hot ingots of one metal on it (up to 9), right-click with the hammer and pick what to make. Its ingots become a
  forging blank; the rest come back to you.
- Strike (right-click) in time with the bar under the crosshair: the green zone is perfect, the amber zone good. The
  zones shrink and the arrow speeds up each blow; harder metals (steel) shrink them faster. A dagger takes 3 blows, a
  claymore 8.
- If the metal goes cold before you finish, take the blank back to the hearth; the blows struck so far stay on it.
- Hammers: copper (its head casts, so there is always a first hammer) and steel, both forged at the Tool Station.

## Grades
| Grade | Durability | Attack | Attack speed | Mining speed | Armour |
| --- | --- | --- | --- | --- | --- |
| Poorly Forged | x0.75 | -1 | -0.1 | x0.85 | -1 |
| Well Forged | x1 | 0 | 0 | x1 | 0 |
| Expertly Forged | x1.1 | +0.5 | +0.05 | x1.1 | +0.5 |
| Perfectly Forged | x1.2 | +1 | +0.1 | x1.2 | +1 |
| Masterwork | x1.3 | +2 | +0.15 | x1.3 | +1.5 |

Average score per blow (perfect 1, good 0.6, miss 0): 0.9 Perfect, 0.72 Expert, 0.45 Well, below that Poor. An
all-perfect forging rolls Masterwork 15% of the time (gold name, glint). The head's grade carries onto the tool made
from it at the Tool Station.

## Wider smithing (code: `Wear`, `GradedLoot`, `Provenance`, `ArmourForging`, `SmithingWider`)
- **Broken, not gone.** A graded weapon, tool or armour piece that would break stays at its last point of durability
  as Broken ("Broken: repair it"): no attack bonus, no mining speed, no armour, and it can't be used or swung. Any
  repair (anvil, mending, grindstone merge) frees it. Tinkering tools already broke this way. (Mixin
  `GradedWearMixin` on `ItemStack.hurtAndBreak`; `Wear`.)
- **Graded loot and trades.** Gear in loot chests and in villager and wandering trader trades is graded at random
  (`GradedLoot.LOOT_WEIGHTS` / `TRADE_WEIGHTS`: poor 20, well 60, expert 14, perfect 5, master 1 for loot). In
  Kingdoms, an armourer's, weaponsmith's or toolsmith's wares on a realm's land are graded by the buyer's reputation
  with the realm (`GradedTrades`): the hated get mostly Poorly Forged, strangers Well Forged, the revered up to 5%
  Masterwork; the same buyer sees the same grade on reopening, and rising reputation only raises it. Kingdoms sets
  only the grade id; Powers bakes it the first time the stack is in a pack (`Wear.BAKED`).
- **Maker's mark.** Hammered results carry "Forged by <name>" (`Provenance.FORGED_BY`, the smith's display name, so
  their MCA character name; a costume or mask names them as it does everywhere). It carries onto the tool made from
  the head (`Grades.CARRIERS`).
- **Blacksmith rank.** Kingdoms tags a Journeyman Smith `jaymoskingdoms.smith_rank_1` and a Master `_2`; Powers widens
  the perfect zone by `Provenance.JOURNEYMAN_BONUS` (1.0) / `MASTER_BONUS` (2.0). Each finished forging counts as one
  "forge" order for a smith's job in a town (Kingdoms: `Careers`, "Hammer out N pieces at a smithing anvil").
- **Armour.** Iron, gold and copper helmets, chestplates, leggings and boots are hammered for 5/8/7/4 ingots (blows
  scale with ingots). Their grid recipes are switched off (`tools/tinkering_assets.py` `OFF_ARMOUR`); diamond and
  chainmail armour stay loot-only. No steel armour exists in the pack yet.

## Finishing, casting and blueprints
Code: `Finishing` (entry point, polish, hone), `CastRules`, `Quenching`, `SmithTiers`, `StoneAnvil`, `Blueprints`,
`BlueprintItem`, `DraftingTableBlock`. Lang: `lang/parts/smithing_finishing.json`. Tuning numbers are the constants at the top of each.

### Hammer-only heads and casting
- Claymore, broadsword, longsword, katana, ninjato, war scythe, glaive and halberd heads, and metal Broad Blades and Large Plates,
  can no longer be cast: no cast is made from them and nothing pours into their casts (`Melting.tableResult` runs through
  `CastRules.adjust`). Tooltip: "Too long to cast; hammer it on a Smithing Anvil". Stone ones are still shaped at the Part Builder.
  Every other metal head can be cast or hammered.
- A head from a casting table is Well Forged. A cast made from a graded head remembers that grade (`CAST_GRADE`); heads poured from
  it are one grade lower (a cast of a Well head pours Poor heads). Graded heads don't stack with ungraded ones.

### Quenching
- A freshly hammered head is hot (`HEATED_AT`) and the Tool Station refuses it ("Quench it first").
- Use a hot item on a water cauldron (loses one level), on a water source block, or aim at a water source and use it: hiss and
  steam, the heat is gone. A Hot Ingot dipped becomes a plain ingot; a forging blank loses its heat (reheat it to carry on).
- Our twist: a graded head left to cool in air (60 s, in your pack or on the anvil) comes out one grade lower.

### Polishing and sharpening (sneak-use on a vanilla grindstone)
- Blade and axe heads (swords, daggers, axes, glaives, scythes, halberds, knives, saws; small blade, small axe, broad blade, broad axe
  heads) come off the anvil ROUGH. A rough head makes tools of at most Expert. Sneak-use it on a grindstone to polish it. Polishing
  while it is still hot costs a grade.
- Sneak-use a worn tool or weapon on a grindstone to restore 25% of its maximum durability. Each honing takes 2% off its
  maximum durability for good (at most 25 honings). Plain use still opens the grindstone, so it doesn't clash with other
  grindstone mods.

### Stone Anvil
- Sneak-hammer a stone block (stone, granite, diorite, andesite, tuff, deepslate) with a smithing hammer. Heads forged on it never
  beat Well Forged, steel-class metals won't work on it, and after 64 finished forgings it crumbles to cobblestone once the last
  piece is taken off. It drops itself when mined.

### Tiers
- Metals with a forging difficulty of 1.25 or more (steel, slimesteel) need an iron Smithing Anvil and a steel Smithing Hammer
  in the main hand.

### Blueprints and the Drafting Table
- Without a blueprint for what you are making, heads cap at Expert. A Blueprint names a shape (a forging without its metal:
  `weapon/claymore_blade`, `part/broad_blade`) and goes in your inventory (anywhere, the highest level counts). Each finished,
  non-Poor forging of its shape adds 1 xp; 5 xp Expert, 15 Perfect, 40 Master. Its level is your cap, never below Expert. A
  Masterwork needs a Master blueprint.
- Drafting Table (paper x2 over planks x4): use it holding a blueprint with paper in your pack; you get a copy one level lower
  (Well-level copies are as low as it goes and are worth no more than having none).
- Wandering traders sometimes sell a blueprint (16 emeralds): data in `villager_trade/tinkering/wandering_trader`, added to the
  trader's uncommon pool. The creative tab has a blueprint for each hammer-only shape.
- Test: `/give @s jaymospowers:blueprint[jaymospowers:blueprint_data={shape:"weapon/claymore_blade",xp:40}]`.
## Knapping (code: `ground/knapping/`, screen `client/tinkering/KnappingScreen`)
- **Sneak + use a loose rock** (in hand, aimed at air or at a hard block such as stone, ore or bricks) opens a 3x3 grid of
  chips. Right click on air still throws the rock, a rock with flint in the other hand still strikes fire, and sneak + use
  on soft ground (dirt, grass) still sets it down. Every rock you hold goes into the grid's rock slot and comes back when
  you close it.
- Click a chip to knock it out (the server keeps the grid; the client only sends the cell). When the chips left are one of
  the shapes in the panel beside the grid (hover a shape to see where its chips lie) the head appears in the output slot.
  Taking it uses up one rock and the next starts whole. Knocking out every chip, or Start over, spoils the rock.
- Shapes (`Knapping.SHAPES`, one 9-character string each): pick head, small axe head, adze head, small blade, arrow head (two
  from one rock), and the weapon heads dagger blade, hatchet head, spear tip, skewer head, knife blade (only when that
  weapon exists: the knife is Balkon's, so it needs WeaponMod). They are the same stone parts the Part Builder makes
  (Granite and Diorite rocks give granite and diorite heads, every other rock plain stone). A static table, not a data
  recipe, because the results carry materials.

## Fletching Station and tiered arrows (code: `tinkering/fletching/`, screen `client/tinkering/FletchingScreen`)
- Right click a vanilla **fletching table** (it did nothing before) to open the station: head, shaft and fletching slots, a
  lingering potion slot, and the result. Shift-click the result to make up to 16 sets.
- Flint + stick + feather: 4 arrows. An **iron or steel nugget** for the head: 4 iron or steel arrows
  (`TieredArrowItem.Tier`: 2.6 and 3.2 base damage, a vanilla arrow's is 2). Any Tinkers arrow head, shaft or fletching
  (flint, stick and feather stand in for the rest) makes a Tinkers arrow, 4 at a time, as at the Tool Station.
- A **lingering potion** in the potion slot turns the arrows of a flint-stick-feather set into **lingering arrows** (4), or
  dips up to 8 plain arrows put in the head slot (the bottle comes back). A lingering arrow hits like a tipped arrow and
  bursts into a small cloud of its potion where it lands (`LingeringArrow`: radius 1.75, 10 s); the arrow is spent.
- The three arrows are in the `minecraft:arrows` tag, so every bow, crossbow and dispenser takes them. Textures:
  `tools/gen_fletching.py`.
