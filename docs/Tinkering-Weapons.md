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

---

# Tinkering: Tinkers' ranged weapons

Six more Tool Station tools: **longbow**, **crossbow**, **javelin**, **arrow**, **throwing axe** and **shuriken**, built from six new
parts. Code: `tinkering/ranged/` (`TinkerBowItem`, `TinkerCrossbowItem`, `TinkerArrowItem`, and `Ranged`, which holds throwing and how
a bow's and arrow's materials change a shot), `ToolStats.ranged` (the maths), `ToolType.Ranged` (each tool's own numbers),
`TinkerTool` (the interface every Tinkering item implements: `TinkerToolItem`, the bow, crossbow and arrow; the shared tick and tooltip
live there). Assets: `tools/tinkering_assets.py` (`ranged_tools`), then `tools/gen_steel.py` as before (it now also draws steel
for the new parts and tools).

## New parts

| Part | Cost | Cast | Made of |
| --- | --- | --- | --- |
| Bow Limb (`bow_limb`) | 3 | yes | wood (and crimson/warped), bone, copper, iron, silver, rose gold, amethyst bronze, pig iron, slimesteel, steel |
| Bow Grip (`bow_grip`) | 3 | yes | the same as limbs |
| Bowstring (`bowstring`) | 1 | no | string, vine, leather |
| Arrow Head (`arrow_head`) | 1 | no | any head material (stone, flint, bone, metals...), also metals at the Part Builder |
| Arrow Shaft (`arrow_shaft`) | 1 | no | wood (and crimson/warped), bone, blaze rod |
| Fletching (`fletching`) | 1 | no | feather, leaves |

Costs are Tinkers'. The six are in the Part Builder (it now has two pages of tool parts, then the weapon heads). Bow limbs and grips have
a gold cast like the other metal-able parts (a cast of each, melt back to their cost); bowstrings and arrow parts are Part Builder only
and take metal there too (`PartType.builderOnly`, Tinkers' "allow uncraftable"). New materials: **Feather** and **Leaves** (fletching
only) and **Blaze Rod** (shaft only), each a plain item (feather, any leaves, blaze rod; one unit each). Limb and grip stats are
Tinkers' (`TinkerMaterial.Limb` and `Grip`, keyed by family, so crimson/warped wood share wood's). Stone, flint and seared stone have
none and make neither (Tinkers gives them none either). Our steel uses Tinkers' steel limb and grip stats.

Limb (durability, draw speed, velocity, accuracy) / grip (durability share, accuracy, melee damage):
wood 60, 0, 0, 0 / 0, 0, 0; bone 100, +.05, -.05, +.05 / -.25, +.05, 1.25; copper 210, -.1, +.05, 0 / -.2, 0, .5; iron 250, -.2, +.1, 0 /
+.1, 0, 2; silver 300, -.05, 0, +.1 / -.1, -.05, 2.25; rose gold 175, +.15, -.25, +.15 / -.3, +.25, 1; amethyst bronze 720, -.15, +.15,
-.05 / 0, +.1, 1.5; pig iron 580, 0, 0, +.1 / +.1, -.15, 2.5; slimesteel 1040, -.05, -.05, +.15 / +.2, -.1, 2.5; steel 775, -.2, +.2,
-.1 / +.05, -.05, 2.75.

## The tools

| Tool | Parts | Gives | Notes |
| --- | --- | --- | --- |
| Longbow | limb, limb, grip, bowstring | 1 | 120 durability before limbs, x1.5; Tool Forge (4 parts) |
| Crossbow | limb, grip, bowstring | 1 | durability x2 |
| Javelin | small blade, tool handle, limb, grip | 1 | attack 3, speed 1.1; Tool Forge (4 parts) |
| Arrow | arrow head, shaft, fletching | 4 | |
| Throwing Axe | arrow head, shaft | 2 | stacks to 16 |
| Shuriken | arrow head, arrow head | 4 | stacks to 16 |

How the numbers work (all constants in `ToolStats` and `ToolType.Ranged`):

* **Durability** (bows, javelin): the average of the limbs (and, on the javelin, the blade) plus the tool's base, times
  (1 + handle share + grip share), times the multiplier. Arrows, axes and shuriken have none: they are plain stacks.
* **Draw speed** = 1 + the limbs' average. The bow's use time counts at that speed (`releaseUsing` scales the held ticks, so a 1.2
  draw is full in 17 ticks); the crossbow's loading time is its vanilla one over it. Quick Charge (from Haste) still works.
* **Velocity** = 1 + the limbs' average (+ an arrow's shaft). The shot's power is multiplied by it.
* **Accuracy** = 0.75 + the limbs' average + the grip + 0.1 for each Aerodynamic (feather). 0.75 is a vanilla bow's spread, 1.0 is none,
  worse than 0.5 doubles it (`Ranged.spread`). A shot's spread = the bow's x the arrow's.
* **Arrow damage** = a plain arrow's 2 + half the head's attack stat (flint 2.6, iron 3, steel 3.5). Fired from anything: a vanilla
  bow, a dispenser, our bows. From our own bows the arrow's shaft (bone +5%, blaze rod +10% velocity) and fletching (feather: more
  accurate) also count. Arrows are `minecraft:arrows` so any bow takes them.
* **Thrown blades**: throwing axe 3.75 + 0.75 x the head's attack, shuriken 2.25 + 0.75 x, javelin 1.2 x its melee attack. Throw speeds
  (blocks per tick) 1.6, 3.0 and 2.4, times the velocity (the javelin's limbs). Throwing axes spread twice a bow's (accuracy 0.5).
* **Modifiers** on bows work as the enchantments of the job: Sharpness is Power (Piercing on the crossbow), Knockback Punch, Fiery Flame,
  Haste Quick Charge (crossbow), Reinforced Unbreaking. On the javelin as on a sword. Arrows and thrown blades take none.

## What each does

* **Longbow** / **Crossbow**: a vanilla `BowItem` / `CrossbowItem` underneath (so ammo search, Infinity, Multishot, Piercing, the pull
  animation, Kingdoms' archery checks, skeletons' preferences all just work). A broken one can't be drawn.
* **Javelin**: held back like a trident (8 ticks at least) and thrown on release, as the Armory's `ThrownWeapon`: it hits the first thing
  it meets, bounces off and lies where it falls (no loyalty needed) for its thrower to pick up, wearing 1 a throw. In the hand it is a
  3-attack spear (Better Combat `spear`).
* **Throwing axe** / **Shuriken**: right mouse throws one from the stack at once (cooldown 10 / 6 ticks), spinning, also a
  `ThrownWeapon`, picked up again where it lands (they stack back by materials).
* Everything has its parts' traits as the other tools do (Stringy bowstrings, Tanned leather, Aerodynamic feathers...).

## Tie-ins

* Item tags: the arrow `minecraft:arrows`; the longbow `minecraft:enchantable/bow` and the crossbow `enchantable/crossbow` (Kingdoms'
  Archer/Marksman code asks for those, and for `ProjectileWeaponItem`/`CrossbowItem`, which they are); `c:tools/bow`, `c:tools/crossbow`,
  `c:tools/ranged_weapon` (the bows, javelin, axe and shuriken).
* The Armory's `ThrownWeapon` asks `Ranged.thrownDamage` / `Ranged.spins` for any item that isn't one of its own (two lines).
* Models: pulling stages from Tinkers' sprites (longbow: three stages by `use_duration`, crossbow: three by `crossbow/pull`, loaded
  shows its bolt or a rocket), broken string, the javelin held big (32 px) and small in the gui.

## Not done / differences from Tinkers

* Tinkers' arrow, throwing axe and shuriken stats aren't from materials (its heads are "statless"); here the head's attack counts so
  materials matter. The arrow's traits other than Aerodynamic don't act on the arrow in flight (no tipped or modifier arrows, no
  "arrow from vanilla arrow" recipe, no crystalshot or other bow modifiers: scope, multishot-like abilities, quiver).
* A fired arrow is a vanilla `Arrow` (its model is the vanilla arrow). Thrown blades show their item model.
* The crossbow does not use Tinkers' blocking-with-it model, its firework special case beyond showing the rocket, or the bow's "ammo
  drawn in the model".
* No fishing rod (not part of this track), no staves.
* Limb/grip stats stand in only for the materials Tinkers gives them: wood, bone and metals. Stone, flint, seared stone, leather
  can't make them.
* Draw speed shortens the real draw; the pulling sprites still follow vanilla timing (a fast bow looks drawn late).
