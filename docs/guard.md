# The guard and the parry (Powers 0.17.67)

Andy (2026-10-27): "https://www.curseforge.com/minecraft/mc-mods/mallard-guard can we incorporate this? I want parries
to be more possible than the system we have, and make combat more dynamic now that we have lockons. Its getting closer
to epic fight mod."

## Why our own
| Mod | Loader / newest | Licence | Can it ship unchanged? |
|---|---|---|---|
| Mallard Guard (zeli-024) | NeoForge 1.21.1 only; Fabric "planned, not started" | All Rights Reserved | No: no Fabric, no 26.3, and ARR |

Ideas only; no code, art or names taken. What it does, from its CurseForge page: one guard button (right click) whose
stance moves from Perfect Parry to Parry to a plain Guarding Block the longer it's held, for weapons, tools and shields,
each window configurable; it pairs with Better Combat.

## Before this patch
- Everyone: vanilla shields only (full block after a 0.25 s raise, no parry).
- Z Fighter (bare hands): parry in the first 6 ticks of the guard. Darktide (bare hands, katana, knife): first 5 ticks.
- Kingdoms Combat Arts chain, finishers on the downed, the dodge and feathers, the lock-on (middle mouse), Z Fighter poise/K.O.

## What we built
- `combat/Guard` (server). Every melee weapon or tool with nothing on right click gets vanilla's `blocks_attacks`
  component (Fabric `DefaultItemComponentEvents`): right click raises it like a shield (third-person arm pose, axes
  knock it aside), it stops 60% of a frontal blow, and each blocked blow costs feathers (0.6 per damage, half-feathers);
  with none left the guard breaks (2 s, a short stagger). It stands aside when the off hand holds a non-weapon (a torch,
  food, a shield or bow still work) and for Darktide's own guard.
- Parry windows, counted from the raise, for that weapon guard and for shields alike:
  - 0-5 ticks: **perfect parry**. Nothing taken (arrows too), the attacker shoved and stunned 1.5 s, a player attacker's
    weapon on a 1 s cooldown, 2 feathers back, and your next blow on them within 2 s does 1.5x.
  - 6-12 ticks: **parry** (melee). Nothing taken, a smaller shove, 0.6 s stun, next blow within 1.25 s does 1.25x.
  - Locked onto the attacker: +2 / +4 ticks on those windows.
  - Mashing: a raise within 15 ticks of the last one, which parried nothing, gets no window. A parry clears it.
- `client/combat/GuardClient`: a small timing bar under the crosshair while the guard is up (white, gold, grey, with a
  red notch moving along), a flash when a parry lands; the lock-on lunge: locked on, with a weapon, a swing at a foe up to
  3 blocks past your reach steps you in.
- `client/mixin/GuardFirstPersonMixin`: the blade turned across in first person while guarding.
- Darktide and Z Fighter parry windows widened to 8 ticks.

## Enemies guard and parry too; attack tells (Kingdoms 0.7.267)
Andy: "Build those ideas too".
- Fighters with a weapon and no shield raise their blade between swings (Kingdoms `MobShields`), shield-bearers their
  shield as before. A blow in the first 8 ticks of that guard is parried; caught with the guard down they still parry
  12% of blows (30% for veterans). A parry restarts your attack timer, slows and pushes you back, and they swing back at
  once. Axes, two-handed weapons and Combat Arts area blows can't be parried by them.
- Every fighter's swing (Better Combat moves) starts with a glint at the weapon: white means you can parry it, red means
  a heavy blow (two-handed or 1.4x+) that Guard won't parry, only block or dodge (`Guard.UNPARRYABLE_TAG`).

## Every mob's blow glints first (Powers `combat/AttackTells`)
Andy: "Can you make every mob use this glint mechanic?"
- Every melee mob that attacks through vanilla's MeleeAttackGoal (zombies, spiders, endermen, iron golems, wolves,
  ravagers, our own monsters) or the MeleeAttack brain behaviour (piglins, hoglins, zoglins) now winds up: the glint
  shows at its weapon (or head), and the blow lands 6 ticks later only if you're still in reach and in sight. Its attack
  cooldown is unchanged. Red for heavy hitters (10+ attack damage or a body 1.35+ wide: iron golems, ravagers, wardens),
  whose blows carry the unparryable tag.
- Custom attacks given the glint by hand: the Burrukai's charge, the Bladeshroom Hunter's cut, ship crews' blades
  (Kingdoms `Crewmen`, now with the same 6-tick wind-up). Kingdoms' Better Combat fighters keep their own wind-up glint.
- Left out: contact damage (slimes, magma cubes, the Wraith flying into you), bows, and powers' own summons.

## Guns glint red; every sailor has a flintlock
Andy: "All guns should red glint too, all sailors should have at least one flintlock as a side weapon, some carry more".
- A red glint and a flint's click at the muzzle 10 ticks before a mob fires a gun: pirates' pistols, guards' muskets
  (Kingdoms `MobCombat.aimed`), and Powers' future gunners before each burst (`FutureGuns.AIM_TICKS`).
- Gun balls and bullets are blocked by a raised guard or shield but never parried (tagged unparryable; any TaCZ bullet
  counts too). Hitscan shots now count as projectiles, not melee, so they don't cost guard feathers or get parried.
- Every ship's crewman who fights takes up a flintlock with their weapons (off hand if free, else main hand): one,
  two for a quarter of them (gunners always two or more), three for a tenth. They fire one after another, each after
  its glint, then reload them all (Kingdoms `Crewmen.sidearm`, `brace`, `pistols`).

## To check in game
1. Sword, empty off hand: right click raises it; a zombie's blow right after raising is a perfect parry (ring, sparks,
   zombie thrown back and frozen), later ones are blocked for feathers.
2. Sword + torch in the off hand: right click places the torch. Sword + shield: the shield blocks.
3. Two swords (Better Combat dual wield): right click still swings the off hand.
4. Locked on, a swing at a foe 4-6 blocks away steps you in.
5. Darktide katana guard and Z Fighter bare-hand guard work as before.
6. A sword-armed guard raises its blade now and then; hitting it right after it does gets parried (sparks, you're pushed back, it swings back).
7. A white glint before a fighter's swing; a red one on two-handed swings, and those can't be parried.
8. A plain zombie glints before each blow and misses if you step back in time; an iron golem's glint is red.
9. A pirate or musketeer glints red with a click before each shot; a raised sword blocks the ball but never parries it.
10. Every ship's crew who fight draw a flintlock; some fire two or three in a row before reloading.

## One timing bar for everything that blocks (Powers 0.18.24)
Andy (2026-10-30): "make the parry meter consistent across all weapons/ powers that block, including z fighters and
darktide", then, on their own windows: "yeah keep that but give a bar that represents their timings".

- Weapons and shields keep `Guard` as before (perfect 0-5 / parry 6-12 ticks, lock-on bonus, mashing rule, red blows and
  shots unparryable). Their bar now also turns grey-only when the raise is too eager to parry.
- Z Fighter's crossed arms and Darktide's guard keep their own parry: one 8-tick window from the raise, a 20-tick
  cooldown after a parry, red heavy blows parried too, and their own outcomes (ki/poise; Flow, empowered R, katana
  riposte, reflect).
- The same bar under the crosshair now draws for them, from their own timings: their window in gold, grey while the
  parry is still cooling down, the red notch moving along it. They call `Guard.showPower(player, start, window, readyAt)`
  when the guard goes up and after a parry, and `Guard.hidePower` when it comes down; the server syncs it to the owner as
  `Guard.SHOWN`.
- Every parry flashes the bar the same way (`Guard.cue`), Darktide's Gedan stance included.
- `Guard.PARRIED` (Fabric event: player, attacker, perfect) fires for every parry of any kind, for drills and quests.
- Not changed: Chrono's Time Flow counters (a held blow answered by right click, not a guard), mobs' own parries
  (Kingdoms `MobParry`), Wind/Gravity projectile deflects, mana spirits' parries.

## Knives parry, daggers throw (Powers 0.18.63)
Andy (2026-10-30): "daggers and knives should be further differentiated, knife is for parrying and dagger will still
throw. just swap their damage and name." Before, Balkon's WeaponMod knife and our Armory dagger both threw on right
click; only the Stone Age primitive and bone knives parried. Andy picked: the WeaponMod knife parries (`armory/KnifeGuard`).
- Right click with a WeaponMod knife (`weaponmod:knife.<tier>`) raises this weapon guard, drawn as a block; letting go
  throws nothing. **Sneak + right click** still throws it, WeaponMod's own way (as it already did for Darktide users).
- The knife and the Armory dagger of each tier (wood, stone, iron, gold, diamond, netherite) swap their held damage,
  read from their registered attack modifiers at start. Thrown damage and attack speed are unchanged. Copper knives
  have no Armory dagger and keep theirs.
- Armory daggers keep their throw, and names are unchanged.
