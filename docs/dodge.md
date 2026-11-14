# Feathers and the dodge (Powers 0.15.5)

Andy (2026-10-21): Feathers and Elenai Dodge 2 "can replace our dodge system and HUD elements". This page is the
clean-room spec the remake was built from (REA-Prompts' clone checklist: behaviour, not code; evidence for each
requirement; unknowns marked), then what we built and where.

## Why our own
| Mod | Loader / newest | Licence | Can it ship unchanged? |
|---|---|---|---|
| Feathers (ElenaiDev) | Forge, up to 1.20.1 | README says CC BY-NC-SA 3.0, GitHub sidebar GPL-3.0, Modrinth LGPL-3.0 | No: no Fabric, no 26.3 |
| Feathers of Fatigue (Darkona's fork) | NeoForge 26.3, 26.2 ... ; Forge 1.20.1 ... | GPL-3.0 | No: NeoForge only (we're Fabric) |
| Elenai Dodge 2 (ElenaiDev) | Forge, 1.20.x | CC BY-NC-SA 3.0 | No: no Fabric, no 26.3 |

Powers is MIT, so neither GPL nor NC-SA code can come in. Nothing of theirs is copied (code, art, names of classes);
the ideas below are the only thing taken.

Sources: github.com/ElenaiDev/Feathers, github.com/ElenaiDev/ElenaiDodge2.0, github.com/Darkona/feathers-of-fatigue
(README, wiki "How It Plays", release v26.2-1.0.0), CurseForge descriptions as quoted by search results.

## Spec: what the originals do (evidence → our rule)
| Behaviour | Evidence | Ours |
|---|---|---|
| A row of feathers above the food bar, two per icon, 20 = full row | FoF wiki "How It Plays" | Same (`Feathers.MAX` 20) |
| Armour weight holds feathers back, heaviest netherite | Feathers CurseForge, FoF wiki | 0.35 per armour point (`ARMOR_WEIGHT`), drawn grey at the left end |
| Regenerates each second; pause after spending that grows with repeated spends, capped | FoF wiki (rates not given: UNKNOWN) | 1 per 10 ticks after 20 + 10 per spend in a row, at most 60 ticks |
| Strain: red feathers past empty (default 6), repaid first and slowly | FoF wiki | Same, repaid at half speed, double at rest (still or crouching) |
| Exhaustion: no strain left → can't exert until 30% back | FoF wiki | Same (`RECOVER_SHARE` 0.3): any feather or strain left lets one more move through, and running past the strain exhausts you; the row goes pale and shakes |
| Rest speeds strain repayment; sleep restores everything | FoF wiki | Same (a sleep long enough to count) |
| Cold stops (Feathers) / slows (FoF) regen | Feathers CurseForge, FoF wiki | Halves it: snowy ground or freezing (`COLD_REGEN`) |
| Heat: Nether / fire hold feathers back (4 in FoF's example), Fire Resistance stops it | FoF wiki | 4 held back in the Nether, none with Fire Resistance |
| Hunger ≤ 6 slows regen (FoF, off by default) | FoF wiki | On: halves it (`HUNGRY_FOOD` 6) |
| Endurance potion: golden bonus feathers on top | Feathers, FoF wiki | Golden feathers come from powers instead (`Feathers.addBonus`) |
| Other mods spend feathers through an API | Feathers README, FoF wiki | `Feathers.canSpend / spend / refund` |
| Dodge key, default Alt; alternative: double-tap a movement key | Elenai Dodge 2 CurseForge | Left Alt (`key.jaymospowers.dodge`) and our double-tap Sneak (kept from Combat Roll days) |
| Dodging costs feathers; armour weight matters | Elenai Dodge 2 CurseForge | 6 (three icons) a dodge (`Dodge.COST`); armour shortens the row |
| Dodge distance, cooldown, i-frames | not found: UNKNOWN | 0.85 b/t hop + 0.2 up, 10-tick cooldown, 5-tick window where melee and projectiles miss |
| Mounts with their own feathers, potions, Curios ring | FoF wiki | Not built |
| Sprint and jump costs | FoF wiki | Only in armour, by its weight (see "Fighting in armour" below); unarmoured sprinting and freerunning stay free |

## What we built
- `stamina/Feathers` (server; synced to the owner as one packed long, `jaymospowers:feathers`): the row, weight, heat,
  strain, exhaustion, regen and its slowdowns, sleep, golden bonuses. Spending order golden → row → strain; regrowth
  strain → row → golden.
- `stamina/Dodge`: the `dodge` packet (client → server), the rules (`canDodge`: on the ground, `Ledge.canHang`, not
  on a ledge, not using an item), the cooldown, the evasion window (`ALLOW_DAMAGE`; never for damage that bypasses
  invulnerability or explosions), hunger 0.4, sound and puff, and `Dodge.onDodge` for powers.
- `client/stamina/DodgeClient`: the key and the double-tap Sneak (with Combat Roll's old guards: not while clinging or
  hanging from a bar, nor 10 ticks after; not on Web's double-Sneak yank), direction from the movement keys turned by
  your facing, back with none held. The hop is applied on the client at once.
- `client/stamina/FeathersHud`: the row, right above hunger, moved up for air bubbles and a mount's hearts. Art
  `textures/gui/feathers.png` from `tools/gen_feathers.py` (9x9 icons on vanilla's HUD grid).
- Powers (Andy: the powers that had extra dodges get extra feathers): Darktide +12 golden (`DarktideDodges`; dashes cost a dodge's feathers, a landed flurry refunds them; the
  evasion Flow now fires on our dodge), Web +20 golden (`WebRolls`; each counter spends a dodge's worth), Corrupted
  mode +12 golden and every dodge is the blink (`ZFighterCorruption`), Chrono +12 golden (`ChronoFlow.DODGE_BONUS`; its own Time Flow roll charges stay as they were).
- Combat Roll left the pack (its HUD and its enchantments with it).
- (0.15.74) The roll animation is ours now: `client/stamina/DodgeRollPose`, synced by `Dodge.ROLLING`.

- The flip: Dodge pressed in the air off a jump, with a weapon in hand (`combat/Armed.kind`) and Weapon Forms learnt
  (`KingdomsCompat.knowsForms`), turns a somersault the way you hold (`Dodge.canFlip`, `Dodge.Flip`, `FLIP_*`): once a
  jump, `FLIP_COST` feathers, a small push, the same evasion window, and a lift only for `Dodge.flipLift` (a `LIFT_POWERS` holder, anyone who may fly, Strength, brawn). `Dodge.Rolling.flip` tells everyone's
  `DodgeRollPose` to turn it in the air (no drop, the weapon arm left out), and your first-person view turns with it.

## Fighting in armour (Andy's picks from Knights & Heraldry, ideas only: it is all rights reserved)
Three things, all in Powers, none with a bar or a screen of its own.
- **Fighting stamina** (`stamina/Exertion`, on `Feathers.drain`): armour has a load, 0 (nothing on) to 1 (plate head to
  foot; mail weighs 0.6 of plate, padding 0.2, by piece: chest 0.4, legs 0.3, helmet and boots 0.15 each,
  `core/ArmourKind.load`). In full plate sprinting wears 2.6 half-feathers a second off the row (regrowth is 2), a jump
  1, a swing 1.5, a blow taken 2; everything is times the load, so the unarmoured tire of none of it. It wears off
  golden feathers, the row, then the red strain, set against the regrowth and without the pause a dodge brings. Run dry (the pale, shaking row; by
  this or by over-dodging) and the next blow that lands knocks the weapon out of your hand, a few blocks away, whole
  (`DroppedArms.knock`), once each time you run dry. Adrenaline halves the wear and keeps the hand closed; the downed
  tire of nothing.
- **Weapon sweet spot** (`combat/Blows.measure`, `combat/WeaponKind`): each kind of weapon has a band of its own reach
  it hits hardest in (dagger, claw, fist weapon 0-60%; axe, mace 30-80%; sword 35-85%; rapier 40-90%; greatsword
  45-90%; hammer 40-88%; staff 50-92%; polearm 55-95%; spear 60-97%). The middle 30% of the band does 1.1x; outside it
  the blow falls to 0.7x by 30% of the reach further out or in. Distance is eye to the nearest of the body struck; the
  reach is Better Combat's for the weapon (two-handed bonus included), else vanilla's. Players' attacks and spear
  stabs, and Kingdoms' fighters when they swing a weapon's own moves (`jaymospowers.weapon_swing`); Weapon Forms' and
  powers' scripted blows are left alone. A ring in your ear at the heart, a dull thud off it.
- **Cut, thrust, blunt** (`Blows.meetArmour`, `core/ArmourKind`): a blow's damage before armour is times

  | | padding | mail | plate |
  |---|---|---|---|
  | cut (swords, greatswords, claws) | 1.15 | 0.6 | 0.7 |
  | thrust (daggers, rapiers, spears) | 1.0 | 1.25 | 1.1 |
  | blunt (maces, hammers, staves, fist weapons) | 0.75 | 1.1 | 1.35 |

  An axe is a cut and a blunt blow evened out (0.95 / 0.85 / 1.025), a polearm a cut and a thrust (1.075 / 0.925 /
  0.9). The piece asked is the one over the lock-on's region (head: helmet; torso, arms: chest piece; legs: leggings),
  else the chest piece; a bare place or a broken piece is 1. Armour wears by what it is asked to stop, so a mace wears
  plate faster still. A piece's kind: the tags `jaymospowers:cloth_armor`, `mail_armor`, `plate_armor` (Kingdoms adds
  its sets and Epic Knights' to them), else its name, else its equip sound, else plate. Weapons and armour say theirs
  in one grey tooltip line.

## To check in game
1. The row sits right above hunger; air bubbles and a horse's hearts push it up, not over it.
2. Full netherite leaves 13 of 20 (seven grey half-feathers at the left).
3. Alt and double Sneak dodge left/right/back/forward by your keys; no dodge in the air, on a ledge, clinging, eating.
4. A zombie's swing or a skeleton's arrow landing right as you dodge misses (a sweep puff).
5. Unarmoured: three dodges leave one icon; the fourth runs into red strain; the fifth spends the last of the strain
   and the row goes pale and shakes, with no dodging, until it's a third back.
6. Darktide's Riptide Dash, Web's counters and the Corrupted blink still work and draw golden feathers first.
7. Freerunning: dropping off a cling with Sneak never dodges.
8. Unarmoured: sprinting, jumping and fighting never touch the row. In full iron: a straight sprint empties it in about
   25 seconds and leaves it pale about 4 seconds later; standing still it comes back.
9. Pale row, then take a zombie's hit: the sword flies out of your hand, lands a few blocks behind you and can be
   picked up after a moment. A second hit while still pale knocks nothing more.
10. A sword on a leather, a chainmail and an iron armour stand's worth of zombie: the chainmail one clinks and takes
    least; a mace on the iron one crunches and takes most. Tooltips name each piece and each weapon.
11. A spear at full reach rings; the same spear point-blank thuds and does about 0.7 of it. A dagger is the other way round.
