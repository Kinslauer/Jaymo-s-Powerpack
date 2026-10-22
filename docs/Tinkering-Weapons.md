# Tinkering: Tinkers' melee weapons and big tools

Ten more Tool Station tools after Tinkers' Construct 3 (1.20.1), whose tool definitions (parts, base stats, multipliers, traits)
and sprites they use. Code: `tinkering/ToolType` (the entries), `ToolStats` (the maths, unchanged: heads give durability, speed,
attack and tier, handles scale them, then the tool's multipliers), `ToolAreas` (the shapes of an area), `TraitEffects.areaBreak`
(breaks an area), `ToolActions` (harvest, shears, dual wield, bonking, shoves). Assets: `tools/tinkering_assets.py`, then
`tools/gen_steel.py`, then (as before) `tools/weapon_parts_assets.py` (the script wipes the weapon part sprites: restore them
from git if you don't have the WeaponMod jar to hand).

Tinkers' constants: `attack` and `speed` are its base attack damage and attack speed; the three multipliers apply to the tool's
attack, durability and mining speed. Slot counts (abilities/upgrades) aren't used: every tool's modifier slots come from its level.

## New parts

| Part | Cost | Kind | Cast | Used in |
| --- | --- | --- | --- | --- |
| Broad Axe Head (`broad_axe_head`) | 8 | head | yes | broad axe |
| Tough Binding (`tough_binding`) | 3 | binding | yes | scythe, broad axe, excavator, vein hammer |

Both are in the Part Builder's list (page 1, 13 of 16 cells used), have a gold cast each (pour gold over one on a casting table),
melt back into their cost like every part, and a Part Builder icon. A binding takes any binding material (string, vine, leather
or any metal/stone), like the tool binding.

## The tools

Parts are in slot order; `x` is the part's share of its kind's stats.

| Tool | Parts | Attack | Speed | Atk x | Dur x | Mine x | Traits | Mines | Area |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| Dagger | small blade, tool handle | 3.0 | 2.0 | 0.65 | 0.75 | 0.75 | Padded, Dual Wield | hoe blocks, cobwebs (sword style) | none |
| Kama | small blade, tool handle, tool binding | 1.0 | 1.6 | 0.5 | 1 | 1 | Tilling, Shearing, Harvest | hoe blocks | ball, radius 1.5 (19 blocks) |
| Scythe | broad blade, tough handle x0.5, tough binding, tough handle x0.5 | 1.0 | 0.7 | 1 | 2.5 | 0.45 | Tilling, Shearing, Harvest, Sweeping II | hoe blocks | 3x3 face, 3 deep |
| Broad Axe | broad axe head x0.75, tough handle, pick head x0.25, tough binding | 5.0 | 0.6 | 1.65 | 4.25 | 0.3 | Stripping, Timber; breaks shields (5 s) | axe blocks | tree, else the block and 5 above |
| Excavator | large plate, tough handle x0.5, tough binding, tough handle x0.5 | 1.5 | 1.0 | 1.2 | 3.75 | 0.3 | Knockback II, Pathing | shovel blocks | 3x3 face |
| Vein Hammer | hammer head x0.75, tough handle, tough binding, large plate x0.25 | 3.0 | 0.85 | 1.25 | 5.0 | 0.3 | Piercing II, Vein Mining | pickaxe blocks | ore vein |
| Pickadze | pick head x0.5, tool handle, adze head x0.5 | 0.5 | 1.3 | 1.15 | 1.3 | 0.75 | Pathing | pickaxe and shovel blocks | the block and the one above (1x2) |
| War Pick | pick head, tough handle, tool binding | 0.5 | 1.2 | 1 | 1 | 1 | War Charge | pickaxe blocks | 3x3 flat |
| Minotaur Axe | small axe head, small axe head, tool handle | 3.0 | 0.9 | 1 | 1 | 1 | Charge Attack, Stripping; breaks shields (5 s) | axe blocks | the block and 5 above |
| Battlesign | large plate, tough handle | 2.0 | 1.2 | 1 | 1 | 1 | Blocking, Bonking, Knockback | nothing | none |

Every tool also has its parts' material traits, as before (two bone parts make Piercing II). Excavator, scythe, broad axe and
vein hammer are held big, like the sledge hammer. The pickadze's materials mine no better than wood level (Tinkers caps it at
gold); the Emerald, Diamond and Netherite modifiers still lift it.

### What the traits do

* **Wide Swing (Area)**: after you break a block the tool breaks the rest of its area (the shapes above) too: only blocks it is
  made for (never a chest, never a half-grown crop with Harvest), no more than 1.5 harder than the first, each worn as normal.
  Sneak to break just one. The sledge hammer's 3x3 is the same code (its old rules are kept for it exactly).
  The box is laid out by the face you hit: across it, up it (on a floor or ceiling, the way you look), into the wall.
* **Timber (broad axe)**: cutting a log that belongs to a tree (some leaf next to the logs that grew, not one placed) fells
  the whole tree upward: up to 128 logs within 8 blocks. A log cabin isn't a tree; it just takes the column.
* **Vein Mining (vein hammer)**: mining an ore (`c:ores`) breaks every block of the same ore joined to it (gaps of one block
  count as joined), up to 48 blocks within 8 blocks (`VEIN_LINK`, `VEIN_MAX`, `VEIN_RADIUS` in `ToolAreas`). Needs the tier
  the ore needs, per block.
* **Harvest (kama, scythe)**: a grown crop (wheat, carrots, potatoes, beetroot, nether wart, cocoa...) broken with it, or right
  clicked (the crop or the soil under it), is picked and replanted at age 0: its drops come out minus one of its seeds; if the
  drops hold no seed it just breaks. Right click also picks every grown crop in the tool's area. Each crop wears the tool 1.
  Sneak to break a crop for real.
* **Shearing (kama, scythe)**: right click shears a sheep (anything shearable: mooshroom, snow golem); leaves, cobwebs, vines,
  lichen, grass, ferns and other plants cut with it drop themselves, as for shears. It also mines cobwebs at speed 15, wool at 5.
* **Tilling / Pathing / Stripping**: right click does what a hoe / shovel / axe does (vanilla block transformers).
* **Padded (dagger)**: a hit keeps only a quarter of its sideways knockback.
* **Knockback (excavator II, battlesign)**: a hit pushes 0.5 a level further (less for what resists knockback).
* **Dual Wield (dagger)**: held in the off hand, right mouse swings it at what you look at (the usual reach) on its own cooldown
  of 20 / its attack speed ticks (0.5 s at speed 2). It hits for its own attack plus its enchantments (not the main hand's),
  pushes a little and applies its traits. With Better Combat the plain attack key already alternates hands; this is Tinkers' right
  click swing.
* **Charge Attack (minotaur axe)**: a hit while sprinting deals 3 extra damage a level.
* **War Charge (war pick)**: each block mined stacks a charge (up to 5, gone after 5 s idle); the next hit spends them all for 1.5
  extra damage each.
* **Blocking (battlesign)**: held up with right mouse it blocks half the damage that comes at its front 120 degrees (0.15 s to
  raise), wearing the sign, like a shield.
* **Bonking (battlesign)**: let go of the held-up guard after 12 ticks and what you're looking at within 3.5 blocks takes half the
  sign's attack, is shoved 1.6 blocks away and up a little; 2 wear; 2 s before it can bonk again.

### Not done / differences from Tinkers

* The war pick, minotaur axe and battlesign are material-built in Tinkers (head/limb/bowstring, head/head/handle, head/plating);
  here they are put together from parts (see the table) so the Tool Station can tell them apart, which changes their stats to
  the usual part maths. The war pick's bowstring layer is left off its sprite.
* No area tilling for the scythe/kama (just the block you click), no tripwire disarming, no modifier that widens an area.
* Tinkers' `silky_shears` (dagger, sword, cleaver: shears only with the Silky modifier) isn't added.
* The Blocking guard doesn't use the shield's damage-type exceptions (fall and drowning have no direction so it ignores them
  anyway).
* The Tool Forge rule (more than 3 parts) is another track's: the scythe, broad axe, excavator and vein hammer have 4 parts.

### Tie-ins

* Item tags: dagger `swords`; kama and scythe `hoes` + `swords`; broad axe and minotaur axe `axes`; excavator `shovels`;
  vein hammer and war pick `pickaxes`; pickadze `pickaxes` + `shovels`; battlesign none (a weapon by its attack).
* Better Combat: weapon_attributes for each: dagger `jaymospowers:combo_dagger`, scythe `jaymospowers:combo_war_scythe`,
  kama `sickle`, broad axe `heavy_axe`, excavator and vein hammer `hammer`, pickadze and war pick `pickaxe`, minotaur axe
  `double_axe`, battlesign `mace`.
* Finishers: `kama` joins the dagger words, `excavator` and `battlesign` the blunt ones (the scythe is already a polearm by name,
  the axes are axes, the vein hammer a hammer, the war pick and pickadze blunt by the `pickaxes` tag).
* Steel (`tools/gen_steel.py`) is drawn for every new part and tool; the sprites of materials Tinkers draws none of for the war
  pick, minotaur axe and battlesign (wood crimson/warped, granite, diorite, blackstone, and a few more) are the tool's grey
  template in that material's colours, read off the pickaxe.
