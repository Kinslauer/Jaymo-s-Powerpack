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
| Mounts with their own feathers, potions, Curios ring, sprint/jump costs | FoF wiki | Not built (sprinting and freerunning stay free: Andy can ask) |

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

## To check in game
1. The row sits right above hunger; air bubbles and a horse's hearts push it up, not over it.
2. Full netherite leaves 13 of 20 (seven grey half-feathers at the left).
3. Alt and double Sneak dodge left/right/back/forward by your keys; no dodge in the air, on a ledge, clinging, eating.
4. A zombie's swing or a skeleton's arrow landing right as you dodge misses (a sweep puff).
5. Unarmoured: three dodges leave one icon; the fourth runs into red strain; the fifth spends the last of the strain
   and the row goes pale and shakes, with no dodging, until it's a third back.
6. Darktide's Riptide Dash, Web's counters and the Corrupted blink still work and draw golden feathers first.
7. Freerunning: dropping off a cling with Sneak never dodges.
