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

## One parry for everything that blocks (Powers 0.18.24)
Andy (2026-10-30): "make the parry meter consistent across all weapons/ powers that block, including z fighters and
darktide".

Before: weapons and shields used `Guard` (perfect 0-5 / parry 6-12 ticks, lock-on bonus, mashing rule, unparryable
red blows, timing bar). Z Fighter's crossed arms and Darktide's guard each had their own single 8-tick window, a 20-tick
cooldown after a parry, their own stun/shove/counter, no timing bar, and they parried red heavy blows and gunshots.

Now `combat/Guard` is the one parry:
- A power's guard calls `Guard.powerRaised` / `powerLowered` when it goes up and down, asks `Guard.timing(...)` what a blow
  meets (BLOCK, PARRY or PERFECT), and answers a parry with `Guard.parried(...)`: the same windows, lock-on bonus,
  mashing rule, unparryable blows and shots, the same stun, shove, riposte (1.5x / 1.25x on your next blow), perfect-parry
  feathers and the same sparks, ring and bar flash.
- Each power adds only its flavour: Z Fighter +10 ki and poise wear; Darktide +8 Flow, the next R empowered, the katana's
  instant slash on humanoids and the blade's reflect. Blocking (what isn't parried) still costs each its own resource
  (feathers, ki, Flow) and breaks its own way.
- `GuardClient`'s timing bar draws for all three, from the server's raise (`Guard.SHOWN`, owner only); a raise too eager
  to parry shows grey only.
- Darktide's Gedan stance keeps its own hold-to-parry, but flashes the bar and counts as a parry.
- `Guard.PARRIED` (Fabric event: player, attacker, perfect) fires for every parry of any kind, for drills and quests.
- Not changed: Chrono's Time Flow counters (a held blow answered by right click, not a guard), mobs' own parries
  (Kingdoms `MobParry`), Wind/Gravity projectile deflects, mana spirits' parries.
