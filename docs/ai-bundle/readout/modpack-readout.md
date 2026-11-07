# Jaymo's modpack: everything it adds

Written 2026-10-11 from the code on master: **Jaymo's Powers 0.8.22**, **Jaymo's Kingdoms 0.5.0**, Jaymo's Kitchen.
Code is the source of truth here; where `docs/Abilities.md` (still v0.7.0) or the design docs disagree, this says so.
Paths are relative to the repo root. Sections that shorten paths (`P/`, `C/`) say what they stand for at their top.

**Default keys:** R / G / V / B = abilities 1-4, C = ultimate (ascended), Z = mode, H = powers menu, X = power skill
(Web's symbiote menu; Wind's Tempest Kick).

## Contents
1. How powers work (awakening, Mastery, Fate), then Gravity, Wind Control, Pocket Arsenal, items/worldgen, commands
2. Chronokinesis (detailed, for the rework)
3. Web Slinger (detailed, for bug fixing)
4. Jaymo's Kingdoms, Jaymo's Kitchen, third-party mods in the pack

**Wind kick, quick pointer:** the Tempest Kick pose is client side in
`src/client/java/com/jaymospowers/client/power/wind/WindPose.java` (leg keyframes :90-117 with
`LEG_CHAMBER/APEX/THROUGH` at :112-114, `kickLean()` :321, `kickLimbs()` :346, `hipBend()` :429, applied :459-461).
Timing and the crescent are in `src/main/java/com/jaymospowers/power/wind/WindKick.java` (timing constants :58-62, `kick()` :154, `snap()` :183).

---


Written from the code on master (`gradle.properties` version=0.8.22). `docs/Abilities.md` dates from 0.7.0 and is
stale; where a design doc and the code disagree, this follows the code and says so. Times are given in seconds
(20 ticks = 1 s). "Mastery level" means how many of the 4 abilities are unlocked.

## How powers work

**The five powers.** Each is registered from `JaymosPowers.onInitialize()` in this order: Wind Control, Pocket
Arsenal, Web Slinger, Chronokinesis, Gravity. The interface is `api/Power.java` (hooks: `onKey`, `onMode`,
`onUltimate`, `onAscended/Corrupted/Purified`, `starterKey`, `unlockOrder`, `usableEarly`). The registry, who holds
which power, and awakening all live in `core/Powers.java`. Your power is saved in the attachment
`jaymospowers:power`, kept through death and synced to every client.

**Getting a power.**
- **Natural awakening** (`Powers.tick` / `Powers.awaken`): a powerless survival player is given a random timer of
  24000-72000 ticks (20-60 minutes of play, `AWAKEN_MIN/MAX_TICKS`). It only counts down while they're alive and not
  in creative or spectator. An action-bar whisper comes 5 minutes and 1 minute before it fires. Then a random power
  awakens (never the one you already have), with a title, the totem sound and particles.
- **Affinity** (`core/Affinity.java`, `AffinityScreen`) would let a player pick the power that awakens. It is switched
  **off** (`ENABLED = false`), so awakenings are random. `/power affinity` still stores a choice.
- **Creative / operators**: the H screen has a Choose button on each card (`Powers.choose`; creative or gamemaster
  permission). `/power set` does the same thing. Both give the power with every ability unlocked.
- **There are no items that grant a power.** The items each power has are for ascending it (see each power).

**Mastery** (`core/Mastery.java`). A natural awakening starts with only the power's weak **starter** (`starterKey()`,
or a passive one). Abilities then unlock one at a time in the power's `unlockOrder()` at 20 / 60 / 120 / 200 points
(`UNLOCK_AT`). You earn 1 point per minute of survival play (`TICKS_PER_TIME_POINT` 1200), plus 1 per ability use, at
most once every 5 s (`USE_CREDIT_SPACING` 100). Each unlock shows a title and a chat line with the key. Pressing a
locked key shows "locked" on the action bar. A power given by creative or command has no `UNLOCKED` attachment, which
means all 4 are unlocked. `/power level <0-4>` sets the level directly.

**Fate: ascension, corruption, cures** (`core/Fate.java`; one synced attachment, `jaymospowers:fate`).
- **Ascend:** this is only possible once all 4 abilities are mastered (`Fate.canAscend`). Each power has its own
  milestone or item. Ascending gives a new form name and the C ability, and also counts as a level-up.
- **Corrupt:** a 0-100 meter (`MAX_CORRUPTION`). Each power fills it its own way, and some corrupt you outright. Creative
  players are spared.
- **Purify:** each power has its own circumstance in the world. Purifying is permanent: the power can never be
  corrupted again.
- Ascended and corrupted are independent, so a power can be both. Each power has 4 names (base, `.ascended`,
  `.corrupted`, `.ascended_corrupted` in lang).
- C only reaches a power that is ascended, or corrupted if the power returns `ultimateWhenCorrupted()` (Gravity does).
- Changing your power resets fate (`Fate.reset`). Messages are deliberately vague, because players are meant to
  discover all of this in play.

**Keys** (`client/JaymosPowersClient.java:30,47-52`; Web's X at `client/power/web/WebSymbioteClient.java:222`;
category "Powers" under Controls):

| Default | Mapping id | Name in Controls | Use |
|---|---|---|---|
| R / G / V / B | `key.jaymospowers.ability_1..4` | Power Ability 1-4 | the four abilities |
| C | `key.jaymospowers.ultimate` | Power Ability 5 | ascended (or corrupted) ultimate; silent otherwise |
| Z | `key.jaymospowers.mode` | Power Mode | per power (Wind: modes; Web: Zip) |
| X | `key.jaymospowers.web_symbiote` | Power Skill (Symbiote menu, Tempest Kick) | Web symbiote menu; Wind Tempest Kick (tap) and Cyclone (hold) |
| H | `key.jaymospowers.menu` | Powers Menu | opens PowerScreen |

`KeyConflicts` (client) runs once per install (it writes a marker file, `config/jaymospowers-keys-v1`). It moves
other mods' default bindings off these keys: Xaero's waypoint/map, Pantry, Ok Zoomer, quickActions, the vanilla
toolbar save/load keys. `CombatRollTaps`: when Combat Roll is installed, double-tapping Sneak within 6 ticks rolls,
because R belongs to the powers.

**PowerScreen (H)** (`client/PowerScreen.java`, with the text lines from `client/KeyGuide.java`). In creative, it shows
every power as a card (2-3 columns, scrollable) with one line per key and a Choose button. In survival, it shows only
your own power, with lines that follow your state: starter, ascended, corrupted, both, and the Z/X/C special lines. It
also shows the mastery line and, if you have no power yet, that it's dormant. Key names follow your actual bindings.

**HUDs.** `client/PowerHud.java` draws a badge top-left: the power's icon and name as fate shaped it (corrupted badge
0x8A3FB5 with crack pixels). It then hands off to the active power's own `PowerClient.renderHud`, whose chips sit at
the bottom right, next to the hotbar (Wind's chip style is the house style: `client/power/wind/WindHud.java`).
Cooldowns are synced attachments (for example `GravityCooldowns`, `ArsenalPower.Cooldowns`).

**Carry** (`core/Carry.java`, client `CarryClient`/`CarryPose`, mixins `Carry*`; design in `docs/carry.md`). Anyone
can sneak + hold right-click (0.6 s, a bar; 0.17.7) on a player, a small animal (the `jaymospowers:carryable` tag: wolf, cat, ocelot, fox, chicken,
rabbit, parrot, frog, armadillo) or any baby animal to pick it up. While carrying, right-click puts it down and sneak +
right-click throws it. A carried player gets free with one jump unless stunned. The carried body is pinned to the
carrier every tick rather than riding, and its AI is paused (`GravityCarriedMobMixin`). Each pickup rolls a hold:
fireman's or bridal, plus shoulder and back in Wind flight; carrying in flight gives ×0.8 speed and ×1.3 hunger.
Player weight slows the carrier by 0.15 (animals 0.05). Gravity users can carry any non-boss mob up to 4 wide and
6 tall, with no slowdown; mobs bigger than 0.8×1.0 go overhead (`GravityWeight`).
- **Corpses** are Jaymo's Kitchen's, not this mod's. Carry refuses while you hold a Kitchen corpse.
  `power/web/KitchenCompat.java` is the only class that touches Kitchen; every call checks Kitchen is loaded and
  reaches its arms-carry by reflection. The symbiote feeds only on corpses it carries. Gravity's Lift can take corpses.

**BlockCluster** (`core/BlockCluster.java`, entity `jaymospowers:block_cluster`, renderer `BlockClusterRenderer`). A
chunk of up to 9 blocks torn out of the world: Gravity's Lift aimed at a block, Web's Living Armor tendrils. You can
hold, swing (3 + 0.8/piece, max 10 damage, 4.5 reach) or throw it (2.5 + 2.5×speed, max 12 damage). Chunks made only
of soft blocks (hardness ≤ 0.6, plus wool) deal at most 1.0. It shatters on impact (30% chance per block to drop),
crumbles after 5 s idle or 2 min old, and is never saved. Shared helpers: `core/Debris.java` (block-coloured smash
dust) and `core/Flames.java` (one fire rule for every fire effect: it lights mobs, corpses, items and creepers, sets
off TNT carts, and makes a nearby symbiote flinch).

**Compat** (all optional except Kitchen):
- **Jaymo's Kitchen:** required in `fabric.mod.json` since 0.8. Touched only through `KitchenCompat`; also by
  `power/gravity/mixin/GravityCartMixin` (Kitchen's `CartEntity`: no drag for Gravity pullers) and
  `WebBehemothClient`.
- **Balkon's WeaponMod** (`suggests`): Arsenal's pull pool reads its weapons from the registry at runtime
  (`ArsenalPool`). `WeaponModProjectileSaveMixin` targets its projectile class by name. The built-in resource pack
  `held_guns` (registered at `JaymosPowersClient.java:58`) restyles its guns in hand.
- **First-person Model:** `client/power/web/FirstPersonCompat`. **Combat Roll:** `CombatRollTaps`.
- **Better Combat** style `data/jaymospowers/weapon_attributes/soras_dagger*.json`.
- **Jaymo's Kingdoms** checks for the Void Crown by id; Powers knows nothing about Kingdoms.

**Config.** There is no config file. Every tuning number is a `static final` at the top of its class. The only file
written to the config folder is KeyConflicts' marker.

## Gravity

Files: `power/gravity/GravityPower.java` (2214 lines: keys, Lift/choke/slam/throw, Well, Weightless, Crush, cave-in,
fate), `GravityPush`, `GravityOrbit`, `GravityRockFlight`, `GravitySkyfall`, `GravityMeteor`, `GravityWeight`,
`GravityCooldowns`, `GravityDebugCommand`; mixins `GravityCartMixin`, `GravityCarriedMobMixin`; client
`client/power/gravity/*` (HUD, arm/board poses, rock-flight controller). Docs: `docs/gravity.md`. Icon: heavy core.
Form names: Gravity, **Event Horizon** (ascended), **Collapse** (corrupted), **Collapsing Horizon** (both).

Unlock order: R (Lift) → V (Weightless) → G (Well) → B (Crush). The starter is a weaker R. V works early: before
Weightless is unlocked, holding V pulls in loose items (12 blocks).

| Key | Ability | What it does (current numbers) | When |
|---|---|---|---|
| R (hold) | Lift (starter) | The creature you aim at floats weightless where you point. Range 14 + 4 per level (`LIFT_RANGE` cap 30), held 3-14 blocks out, up to 6 s, 8 s cooldown. Passive: loose items within 4 + 2/level blocks drift to you, and +0.06 jump. | starter |
| R (hold) at a block | Tear a chunk | A struggle (2 s charge, faster when mastered) rips a BlockCluster loose (3+ pieces; the hardest block it can take grows with level). R again drops a chunk you're holding. | base |
| R + right click | Choke, then slam | Holds the target at 0.75 + half its width and hurts it 1/s. The slam rises for 10 − level ticks, then drives down at 2.2 + 0.7/level; up to 20 damage, breaks blocks from level 3. | base |
| R + left click | Push / throw | Throw at 2.6 b/t for 4 + 5×speed (max 12). Push (`GravityPush`): launched 20 blocks (50 mastered), shoving soft blocks aside, 4 + 3×speed on impact, 5 to anything it passes. | base |
| G | Gravity Well | A point up to 24 blocks away pulls everything within 7 blocks for 4 s. 14 s cooldown. | base |
| V (tap) | Weightless | Your gravity drops (−0.7), so you jump high and fall slow with no fall damage. Costs hunger. Up to 30 s, then 10 s cooldown (2 s if ended early). Also weighs everything within 6 blocks (gravity ×2.5, jump −35%, Slowness). | base |
| V (hold) | Rock flight (earthboard) | Rips up the 3×3 under you and you ride the slab, at most 4 blocks up, always sinking a little. V again steps off. | ascended or corrupted |
| B (hold, release) | Crush | Charge up to 3 s. Everything around the aim point (24 blocks) gets ×3 weight, slammed and pinned for 3 s: radius 2.5-5.5, 4-16 damage, 6-20 s cooldown. Corrupted: ×1.6 damage, +1 radius. | base |
| G then B | Cave-in | Crush released inside your own Well: 14-40 blocks torn up and dropped on the well, 14-36 damage. | base |
| B (both) | Orbit shockwave | Up to 3 torn blocks orbit you (`GravityOrbit`; one tears loose every 1.5 s within 5 blocks). B draws them to the target and flings them, 2 each. G+B rains them one by one onto creatures the well caught. R can take one from the ring. | both |
| C | Meteor | Hold to gather (3 s). A rock 48-128 blocks out (scaling with charge), up to 14× size. 40-60 damage at the centre, falling off over 25-60 blocks, then a shockwave, crater rings and fire (2.5 blocks on the trail, 10 at impact). Cooldown 45 s (tap) to 3 min (full). | ascended |
| C | Singularity | A black hole up to 32 blocks away for 3 s: pulls within 10, 3 magic damage every 0.5 s within 4, a 6 burst at the end. 25 s cooldown. | corrupted |
| C | Skyfall | Everything within 20 blocks falls up for 3 s, then slams down staggered over 1.5 s: fall damage + 0.4/block × 0.6-1.5. 90 s cooldown. With Weightless on, you ride up unharmed. | both |

Always on: Gravity users carry anything but bosses with no slowdown, pull Kitchen carts with no drag, and cancel
armour or held-item movement penalties (`GravityWeight`).
- **Event Horizon (ascended):** full knockback resistance; projectiles within 6 blocks bend away
  (`HORIZON_DEFLECT_RADIUS` 6.0; the doc still says 3).
- **Collapse (corrupted):** gravity +35%, fall damage +25%. Loose items and mobs with ≤10 HP drift to you. Releasing
  Lift drives the target into the ground.

- **Ascend:** 30 "deaths by weight" once you have all 4 abilities (`ASCEND_KILLS`). A death counts if the creature was
  touched by a Gravity ability in the last 10 s and died from a fall, Crush, or the Singularity. Hard falls you survive
  also count. Whispers at 10 and 20.
- **Corrupt:** setting foot in the **Nether** corrupts at once (`GravityPower.java:1767`). The constants
  `CORRUPT_PER_KILL` and `CORRUPT_WEIGHTLESS_TICKS` (the "+2 per kill / +1 per 20 s weightless" meter in
  `docs/gravity.md`) are defined but no longer used.
- **Cure:** 10 continuous seconds under Levitation from something else, such as a shulker
  (`CURE_LEVITATION_TICKS` 200). There's a one-line whisper the first time you levitate.
- **No items.** Debug: `/powerdebug gravity kills <n> | ready | levitate <s>`.

## Wind Control

Files: `power/wind/WindPower.java` (server side of flight: validation, fall damage, trail and boom; the G/V/B
channels), `WindAbility` (channel table), `WindFlight`, `WindModes` (Z modes, Call Lightning, starter cloak),
`WindStorm` (corruption and cures), `WindTempest` + `WindGaleHeart` (ascension, Cyclone Lance), `WindKick`,
`WindCyclone`, `WindWeather`, `WindArmor`, `WindCold`, `WindHeavy`, `WindFumble`, `SorasDagger`, `WindNet`,
`WindDebug`; mixins `WindPlayerAttackMixin`, `CreeperAccess`. Client: `client/power/wind/WindFlightController.java`
(flight is client-authoritative), `WindPose`, `WindCyclonePose`, `WindHud`, `WindAura`, `WindCamera`, `WindTumble`,
and others. Docs: `docs/wind.md`.
Form names: Wind Control, **Tempest** (ascended), **Stormbound** (corrupted), **Thunderhead** (both).

Starter (passive, kept for good): a faint cloak of wind, so you're a little quicker, and holding Jump in mid-air slows
your fall to 0.12 b/t. Unlock order: R (flight) → V → B → G (Tailwind, the charged one, comes last).

**Flight:** hover at 0.55; cruise with Sprint up to `TOP_SPEED` 2.5 b/t × weather; sonic boom at 80%. Sneak locks
speed, S brakes. Turning too hard at speed tumbles you. Flight costs hunger (no cruising at food ≤ 6), but none for the
Tempest.
- Weather scales everything (`WindWeather`): clear day 0.6, clear night / sheltered / other dimensions 0.85, rain
  1.0, thunderstorm 1.3.
- Armour slows flight by 0.06 per weight point (leather and chain are free).
- In the cold (snowy biomes or altitude), every windup and cooldown is 3 s longer (`WindCold`).
- Axes and maces deal 60% damage in a Wind user's hands (`WindHeavy`).
- Until 3 abilities are mastered, each cast has an 8% chance, and each second of flight a 2% chance, of dropping the
  main-hand item (`WindFumble`).

| Key | Ability | What it does | When |
|---|---|---|---|
| (passive) | Cloak | Slightly faster on foot; hold Jump in the air to drift down. | starter |
| R / Sneak + double Jump | Flight on/off | Hover, cruise, sonic (above). Take-off boost 0.9 from the ground, 0.5 in mid-air. | base |
| G (hold up to 5 s) | Tailwind | Speed and Jump Boost for 5-20 s, plus a dash of 0.7-1.5 b/t. 20 s cooldown. | base |
| G (hold, on ground) | Gust blast | A cone (35°, 6-12 blocks) dealing 4-57 damage with 0.6-1.8 knockback. At ≥95% charge it's a TNT-like blast. Cooldown 20 s to 3 min, scaling with charge. Stormbound: lightning where it lands. | base |
| V (hold 2 s) | Gale Barrier | Resistance for 6-18 s and knockback resistance; projectiles within 4.5 blocks are blown back (you become their owner); on release, mobs within 6 blocks are pushed 0.9-1.8. 30 s cooldown. Stormbound: a bolt hits the nearest foe. | base |
| B (hold 2 s) | Zephyr's Breath | Slow Falling 10-30 s and Regeneration 5-12 s for you and players within 6 blocks. 45 s cooldown. | base |
| B (hold) | Call Lightning | Replaces Zephyr in rain or storms: a bolt up to 40 blocks away (radius 2.5) for 6-9 damage, 6-12 hunger, 1-2 static back into you. 6 s cooldown. Under a clear sky B is still Zephyr. | corrupted |
| X (tap) | Tempest Kick | A roundhouse that throws a white crescent: 5 blocks and 6 damage (×1.25 in Tempest mode), 3 s cooldown. It cuts through every creature in its path and stops at walls. Needs all 4 abilities. | base (mastered) |
| X (tap) | Tempest Kick | 20 blocks, 12 damage, 2 s cooldown. | ascended |
| X (hold 8 ticks, on ground) | Cyclone | A superhero landing, then a column of wind (radius 3-6, height 6-12, grows with level). Caught mobs orbit for 2 s taking 1.5 damage every 0.5 s (2.5 ascended or corrupted; corrupted = shock + 2 s burn), then are flung 1.4. It costs you 1 suffocation damage every 2 s (4 s ascended) and hunger. 3 s cooldown. Needs 1 ability. | base |
| Z | Tempest mode | Projectiles within 3.5 blocks deflect; other abilities ×1.25. Drains hunger (needs food ≥ 6), no eating while on. | ascended |
| Z | Stormbound mode | Melee hits stun (16 ticks) and add static; 5 stacks = shocked (rooted 2.5 s, 3 damage). Random zaps on you: 6%/s idle (1.5), 35% per cast (3). | corrupted |
| Z | Thunderhead mode | Both of the above, plus screen-wide lightning flashes for everyone within 48 blocks. Hungriest. | both |
| C | Cyclone Lance | Instant line, 32 blocks, radius 0.6. 14 × max(1, weather) armour-bypassing damage (`jaymospowers:wind_lance`), 1.6 knockback, 6 s cooldown. Stuns 2.5 s while a mode is on. Stormbound: 3 bolts along it. | ascended |

- **Ascend:** kill a Breeze while you have Wind and aren't ascended. Each kill drops a **Gale Heart** 66% of the time
  (`WindTempest.HEART_CHANCE`; the old "every 5th breeze, in flight" rule in `docs/wind.md` is gone). Using the heart
  with all 4 abilities mastered consumes it and ascends you. Anyone else gets a gust in the face and keeps the heart.
- **Corrupt:** using the wind (flying, channelling, barrier up, just after a cast) in a real thunderstorm adds 1 point
  per 36 ticks (100 points is about 3 minutes); out of the storm the meter drains 1 per 15 s.
- **Stormbound** effects: every cast calls visual lightning at its target (5 damage, never at you, never sets blocks
  alight). Gales hit ×2, and the weather always counts as a storm. You move 25% slower on foot and fly at ×0.75 cruise.
- **Cures**, any of three:
  1. Hover at Y ≥ 192 in the Overworld during a thunderstorm for 60 s without casting (the Eye of the Storm).
  2. Wait 3 Minecraft days in world time.
  3. Stand in falling snow for 10 s.
- **Items: Sora's Dagger** (`SorasDagger.java`, recipes in `data/jaymospowers/recipe/`):
  1. **Iron:** crafted from 2 iron, a stick and a feather. 4 damage.
  2. **Gilded:** smithing with a feather template + the dagger + a gold block. 5 damage; wind knockback and puffs.
  3. **Diamond:** breeze rod + gilded dagger + diamond block. 6 damage. Right click = **Flurry**: 5 slashes
     (10 in a Wind user's hand), 7 s cooldown.
  4. **Tempest:** a Tempest (ascended) wind sneak + right-clicks the diamond dagger while carrying a Nether Star,
     Dragon's Breath and a Heavy Core. 8 damage, 7-slash Flurry, 5 s cooldown, gusts, a lunge and an end burst.
  - Its gifts only work for Wind users. The Tempest dagger won't stay in anyone else's hand.
  - The **Gale Heart** is the other Wind item.
- Debug: `/powerdebug wind stormbound <min> | weather | calm <s> | cooldowns | strike`.

**Where the Tempest Kick animation lives (for the roundhouse / dwit chagi rework).**
- **Server (`src/main/java/com/jaymospowers/power/wind/WindKick.java`):**
  - Timing constants at :58-62: `KICK_TICKS` 16, `WINDUP_TICKS` 6, `SNAP_TICKS` 1.5, `RECOVER_TICKS` 8.
  - Swing-trail and crescent-tilt constants at :64-67.
  - The `Kicking` record at :75 (start, which leg, seed whose low 2 bits pick one of 4 arm variants, cold windup).
  - The synced `KICKING` attachment at :101.
  - `kick()` at :154 starts the kick and queues the crescent; `snap()` at :183 draws the arc on the hit tick.
- **Client trigger:** a tap of X sends `WindKick.Kick` from `client/power/wind/WindCycloneClient.java:123` and :128.
- **Pose (`src/client/java/com/jaymospowers/client/power/wind/WindPose.java`):**
  - Kick constants at :90-117: lean, twist, hip bend, and the leg keyframes `LEG_CHAMBER_*`, `LEG_APEX_*`,
    `LEG_THROUGH_*` at :112-114, plus `HIGH_ARM_X`.
  - Render-state extraction into `KICK_KEY` at :230-233.
  - Whole-body lean and twist: `kickLean()` at :321, called from `rotate()` at :259.
  - Limb keyframes: `kickLimbs()` at :346 (chamber, then snap at :361, then recovery), with the four arm variants in
    the switch at :397 and `hipBend()` at :429.
  - Applied from `applyModel()` at :459-461.
- **Mixins that wire the pose in:** `client/power/wind/mixin/AvatarRendererMixin` (extract),
  `LivingEntityRendererMixin` (rotate) and `ModelFeatureRendererMixin` (applyModel).
- **The rework is mostly the client pose.** A roundhouse / dwit chagi means re-keying `kickLimbs` (the chamber → apex →
  through path) and the lean/twist in `kickLean`. `WindKick`'s timing constants set the beats, and its crescent
  direction (`side`, `CRESCENT_TILT`) would follow.

## Pocket Arsenal

Files: `power/arsenal/ArsenalPower.java` (keys, Reach, Return, Barrage, Rummage), `ArsenalPool` (what Reach can
pull), `Summoned` (item component with expiry), `ArsenalReach` (rift timing), `ArsenalStash`, `ArsenalArmory`,
`HundredBlades`, `ArsenalVoid` (corruption and cure), `ArsenalGates`, `ArsenalFlintlock`, `VoidCrown`, `ArsenalItems`
(Dimensional Key), `ArsenalRelics` + `RelicWeapons`, `SwordStone` + `StoneSwords`, entities `entity/RiftBlade`,
`entity/FrostLance`; mixins for slots, bundles, hoppers, item entities and Endermen (keeping summoned items from being
stored or duplicated). Client: `client/power/arsenal/*`. Docs: `docs/arsenal.md`.
Form names: Pocket Arsenal, **Armory** (ascended), **Void Arsenal** (corrupted), **Void Armory** (both).
Unlock order: R, G, V, B.

| Key | Ability | What it does | When |
|---|---|---|---|
| R | Rummage | A small rift pulls out junk (stick, flint, wooden tools, bone); 25% a useful tool, 10% a stone weapon. Lasts 30 s, 30 s cooldown. | starter |
| R | Reach Through | Opens a rift and pulls a random weapon (WeaponMod's guns and blades plus vanilla weapons) with its ammo into your hand. The weapon lasts 75 s (`SUMMON_TICKS` 1500), at most 3 held, 8 s cooldown. Summoned weapons go back when dropped (3 s), when time runs out, or when you die. Claimed relics come out first. | base |
| R | Reach (Armory) | Sneak + R while holding a summoned weapon pins that kind, so every Reach brings it first (empty hand: unpin). | ascended |
| R | Reach (void) | Void-touched weapons: ×1.3 damage, Darkness and Wither on hit. 2% chance the Void Crown comes through. | corrupted |
| R | Void Flintlocks | Reach sometimes brings a pair of single-shot pistols (35%, always the first time): 48 range, 9 damage. Drop a spent one and a loaded one is already in your hand. When pinned, they're all Reach gives you. | both |
| G | Return | Sends the held summoned weapon back and refunds up to 90% of Reach's cooldown (less the longer it was out). | base |
| V | Barrage | 3-5 rifts open one after another (0.5 s apart); each blade hangs for 0.9 s, then flies at your target (48 range, 7 damage). It stays in the world for you to pick up as a summoned weapon. 30 s cooldown. | base |
| V | Void Gates | Replaces Barrage. V fixes a gate where you aim (64 blocks, up to 5; the oldest is replaced). Sneak + V steps through to the gate you aim toward. Holding Sneak + V with no gates casts one ahead (up to 32 blocks). 1.5 s cooldown. | corrupted |
| B | Pocket Stash | Your personal 27-slot chest, saved and kept through death. Summoned items can't go in; claimed relics live here. | base |
| C | Hundred Blades | 5 rifts open over the target (48 blocks) and rain 20 blades, each 3 damage, within 4 blocks. 20 s cooldown. | ascended |

- **Ascend:** use a **Dimensional Key** with all 4 abilities mastered. Keys are found in bastion chests: 25% in
  treasure chests, 5% in the others. Anyone else just hears the lock rattle.
- **Corrupt:** Enderman contact fills the meter: +2 when one aggroes, +4 per hit, +10 per kill, with whispers at
  25/50/75. Setting foot in **the End** corrupts at once (`ArsenalVoid.java:157`).
- **Void Arsenal downsides:** water and rain burn 1/s like an Enderman. A hit of 6 or more has a 30% chance to blink
  you 4-8 blocks away.
- **Cures:** be within 160 blocks when the Ender Dragon dies, having fought it (a 3.5 s purge), or wait 3 Minecraft
  days in world time.

**Items.**
- **Relics:** six named weapons, each with a passive and a right-click active (`RelicWeapons.Kind`), found in themed
  chests. Any chest in the relic tables gives 8% (common) or 25% (rare) for a relic; 75% of the time it's the themed
  one.

  | Relic | Found in | Active |
  |---|---|---|
  | Soul Saber | bastion, nether fortress, ruined portal | 30% life steal passive; soul-fire slash, 14 blocks, 6 damage, 2 s cooldown |
  | Grand Shield | mansion, outpost, stronghold | reflects projectiles when raised |
  | Voidreaver | end city | blink behind your target, 16 blocks, 4 s cooldown |
  | Echo Cleaver | ancient city | sonic boom, 15 blocks, 8 damage, 6 s cooldown |
  | Tidebreaker | shipwreck, buried treasure, ocean ruins | +3 in rain; riptide spin for 6 damage, 3 s cooldown |
  | Sunfang | desert and jungle temples | sets targets alight; sand blast, 6 blocks, 3 damage, 4 s cooldown |

  An Arsenal user who picks a relic up claims it into the Stash (5 stored at most). Anyone else can swing one.
- **Swords in the Stone** (`SwordStone`, `StoneSwords`): five pedestals generated in the world. Only an Arsenal user
  can draw the sword; it becomes a relic. Each has 4 base damage, a passive, an active and one enchantment.

  | Sword | Where | Passive | Active |
  |---|---|---|---|
  | Durandal | Overworld surface | unbreakable, no knockback | Plant: 4-block shockwave, 6 damage, 6 s cooldown |
  | Laevateinn | Nether | burns what it cuts, you don't burn | Flame Wave: 8 blocks, 5 damage, 7 s cooldown |
  | Caladbolg | End outer islands | arcs to 2 more foes at 40% | Rainbow Reach: 10 blocks, 8 damage, 9 s cooldown |
  | Gram | Deep Dark | half of armour ignored, +50% vs bosses and big mobs | Sunder: 9 damage, wrecks armour and shields, 8 s cooldown |
  | Hrunting | frozen peaks, slopes, grove | frost and slow | Frost Lance: thrown, returns, 7 damage, 5 s cooldown |
- **Void Crown:** a helmet with 6 armour and 3 toughness, Curse of Binding. Only a Void Arsenal user can wear it. When
  struck, the wearer steps aside up to 16 blocks, like an Enderman (1.5 s cooldown). It slips off if purified.
- **Dimensional Key:** described above under Ascend.
- Debug: `/powerdebug arsenal corruption <t> <n> | dragonkill | purge <t> | blink <t> | relic | ready <t> | pin <t>`.

## Items, blocks and worldgen

**Items** (all `jaymospowers:`):
- **Wind:** `soras_dagger`, `soras_dagger_gold`, `soras_dagger_diamond`, `soras_dagger_tempest`, `wind_gale_heart`.
- **Arsenal:**
  - `arsenal_dimensional_key`
  - the relics `soul_saber`, `grand_shield`, `voidreaver`, `echo_cleaver`, `tidebreaker`, `sunfang`
  - the stone swords `durandal`, `laevateinn`, `caladbolg`, `gram`, `hrunting`
  - `arsenal_frost_lance`, `flintlock` (Void Flintlock), `void_crown`
- **Chrono:** `chrono_stopped_clock`, which ascends Chrono. It turns up in your hands after a night's sleep once you've
  spent long enough in stopped or rewound time.
- **Web:** `web_ball` (the Web Shot item), `web_symbiote_mass`.
- Gale Heart, Stopped Clock and the Key are kept out of the creative tabs.

**Blocks:**
- `sword_in_stone`, `nether_sword_stone`, `end_sword_stone`, `deep_sword_stone`, `frost_sword_stone`: the pedestals,
  with a `sword` property; they stay put once drawn. Each has a block item.
- `web_bridge`: webline planks you can walk up.
- `web_symbiote_web`: the symbiote's cobweb.
- `web_symbiote_goo`: the crater's goo. It can't be broken or pushed; it bonds to a web slinger who reaches for it.

**Entities:**
- `block_cluster` (core)
- `arsenal_rift_blade` (Barrage blades), `arsenal_frost_lance` (Hrunting)
- `web_ball` (Web Shot); `web_symbiote_blob` (a hostless symbiote looking for a new host, hurt only by fire, lava and
  explosions)
- `chrono_echo` (Chrono's curse: a remnant of you that attacks everything; it only dies for good in the Nether)
- Web's behemoth is a zombie the symbiote has taken over (via a mixin), not a new entity type.

**Worldgen** (`data/jaymospowers/worldgen/`, biomes chosen in code with `BiomeModifications`):

| Feature | Placement | Where |
|---|---|---|
| `sword_stone` (Durandal) | rarity 1/700, surface | Overworld land |
| `sword_stone_nether` | 1/260, y 32-100 | Nether |
| `sword_stone_end` | 1/220, surface | End, not the main island |
| `sword_stone_deep` (Gram) | 1/40, y −52 to −10 | Deep Dark |
| `sword_stone_frost` (Hrunting) | 1/350, surface | snowy slopes, frozen/jagged peaks, grove |
| `web_crater` | 1/500, surface, vegetal step | Overworld, no ocean/river/beach. A 7-11-block meteor bowl with symbiote goo; refuses water, steep ground and structures |

**Other data:**
- Loot: relic and Dimensional Key injections through `LootTableEvents.MODIFY_DROPS`.
- `damage_type/wind_lance.json`, plus `minecraft:bypasses_armor` tag membership.
- Sora's Dagger recipes and recipe advancement.
- `tags/entity_type/carryable.json`.
- `weapon_attributes/soras_dagger*.json`.
- There are no structures (NBT); the only worldgen is the features above.

## Commands

All of these need gamemaster permission (level 2).

**`/power`** (`core/PowerCommand.java`):

| Command | Effect |
|---|---|
| `/power set <targets> <power>` | Give a power (wind, arsenal, web, chrono, gravity), fully mastered, with fate reset. |
| `/power clear <targets>` | Remove the power. |
| `/power awaken <targets>` | Natural awakening now: random, starter only, with the fanfare. |
| `/power level <targets> <0-4>` | Set how many abilities are unlocked (and the matching points). |
| `/power affinity <targets> <power\|fate\|none>` | Store the affinity (has no effect while Affinity is disabled). |
| `/power ascend <targets>` | Ascend now. |
| `/power corrupt <targets> [amount]` | Corrupt outright, or move the meter by ±amount. |
| `/power purify <targets>` | Cure for good. |
| `/power resetfate <targets>` | Clear ascended, corrupted and purified. |
| `/power get <target>` | Show power, level, points, fate; for a powerless player, seconds to awakening and affinity. |

**`/powerdebug <power> ...`** (one class per power):
- gravity: `kills <n>`, `ready`, `levitate <s>`
- wind: `stormbound <min>`, `weather`, `calm <s>`, `cooldowns`, `strike`
- arsenal: `corruption <t> <n>`, `dragonkill`, `purge <t>`, `blink <t>`, `relic`, `ready <t>`, `pin <t>`
- chrono: `echo`, `stasistime <s>`, `timers`
- web: `symbiote bond|reject|feed|level <0-100>`, `swung <blocks>`, `crater`

For testing, the hidden items come from `/give`, for example `/give @s jaymospowers:chrono_stopped_clock`.

---

## Chronokinesis (detailed)

State of `jaymospowers:chrono` on master at v0.8.22, taken from the code. Paths are shortened: `P/` =
`src/main/java/com/jaymospowers/power/chrono/` and `C/` = `src/client/java/com/jaymospowers/client/power/chrono/`.
Times are in ticks (20 = 1 s). The server owns everything. The client draws the HUD, the Time Flow poses and the
Timeless clock hands, and handles Time Flow movement (dashes and counter motion).

### File map

| File | Role |
|---|---|
| `P/ChronoPower.java` (621 lines) | `Power` impl: registration, key dispatch, Nudge, Rewind, Overclock, Bloom glue, habits, sleep→clock, HUD sync |
| `P/ChronoState.java` | Per-player server state held in memory (`STATES` map by UUID): rewind ring buffer, cooldowns, running effects |
| `P/StasisField.java` | G: the freeze bubble, the frozen-entity registry, the frozen-player action locks |
| `P/TimeBloom.java` | B: extra random ticks and block-entity ticks |
| `P/RewindTrail.java` | R: the golden path replayed after a rewind (particles and sound only) |
| `P/ChronoPunch.java` | C (ascended): Chrono Punch flurry |
| `P/ChronoFlow.java` | Z (Timeless + corrupted): Time Flow combat, its attachments and two C2S packets |
| `P/ChronoEcho.java` / `P/ChronoEchoes.java` | The Echo entity (curse) / spawning, returns, following, collapse, corruption |
| `P/ChronoMemory.java` | Persistent player attachment: habits, echo timers, stopped time, bond |
| `P/StoppedClockItem.java` | Ascension item `jaymospowers:chrono_stopped_clock` |
| `P/ChronoSync.java` | S2C HUD packet `jaymospowers:chrono_sync` |
| `P/ChronoHooks.java` | Static entry points that the mixins call |
| `P/ChronoDebugCommand.java` | `/powerdebug chrono echo|stasistime <s>|timers` (gamemaster) |
| `P/mixin/` | `ChronoLivingEntityMixin` (stasis), `ChronoMobStunMixin` (Flow stun), `ChronoBlockItemMixin` (placed habit) |
| `C/ChronoPowerClient.java` | HUD (4 dials and the ult chip), Timeless clock hands, packet receiver, echo renderer registration |
| `C/ChronoFlowClient.java` | Time Flow client: target pick, strike packet, dash, right-click parry, counter motion, combo HUD |
| `C/ChronoFlowPose.java` | Arm, leg and body poses for every Flow move and for Chrono Punch |
| `C/ChronoEchoRenderer.java` | Echo drawn as its owner's skin, translucent and cyan |
| `C/mixin/` | `ChronoAvatarRendererMixin` (puts the pose on the render state), `ChronoStunInputMixin` (stunned player: keys go dead) |
| Mixin configs | `src/main/resources/jaymospowers.chrono.mixins.json`, `src/client/resources/jaymospowers.chrono.client.mixins.json` (both listed in `fabric.mod.json`) |
| Assets | `lang/parts/chrono.json`, `textures/gui/chrono_hud.png` (64×32), `items/` + `models/item/` + `textures/item/chrono_stopped_clock*` |

### Core hooks (how chrono plugs into the shared code)

- **Registration:** `JaymosPowers.onInitialize` → `ChronoPower.init()` (`P/ChronoPower.java:112`). That registers the
  power, the `ChronoSync` payload, the Stasis action locks, the echo entity, the clock item, `ChronoPunch::tick`
  (END_SERVER_TICK), `ChronoFlow.init()`, the debug command, a `PlayerBlockBreakEvents.AFTER` "mined" habit, and
  DISCONNECT / SERVER_STOPPING cleanup. On the client, `JaymosPowersClient` → `ChronoPowerClient.init()` (`C/ChronoPowerClient.java:88`).
- **Attachments:** `JaymosPowers.registerAttachments()` lists `power.chrono.ChronoEchoes`, `power.chrono.ChronoMemory`
  and `power.chrono.ChronoFlow`. Any new class that declares an attachment must be added there, or a dedicated
  server's client won't receive it.
- **Keys:** `core/Powers.key` checks `Mastery.isUsable` (otherwise it shows "locked"), then calls `onKey`, then
  `Mastery.used(player)`. Use credit is rate-limited to once per 100 ticks and is given even if the ability was on
  cooldown or failed. `Powers.mode` → `onMode` for every power. `Powers.ultimate` → `onUltimate`, only if
  `Fate.isAscended` (chrono doesn't override `ultimateWhenCorrupted`).
- **Power hooks used:** `onGained`, `onLost`, `onLevelUp`, `onMode`, `onUltimate`, `tick`, `onKey`, `starterKey` (R),
  `unlockOrder`, `color` (0xF2C14E), `icon` (vanilla Clock). **Not overridden:** `onAscended`, `onCorrupted`,
  `onPurified`, `statusSuffix`, `slotUnlocked`, `usableEarly`, `allowsFloating`, `skipsMovementCheck`.
- **Other core touchpoints:** `core/Carry.canStruggle` (line ~318) returns false while `ChronoFlow.stunned(player)`.
  `client/power/web/mixin/WebModelFeatureRendererMixin` calls `ChronoFlowPose.applyModel` (line 23); this shared
  pose path is owned by the Web package. `GravityPower` only mentions Overclock in a comment.

### Progression (mastery, unlock order, starter)

- Shared `core/Mastery`: `UNLOCK_AT = {0, 20, 60, 120, 200}` points. You get 1 point per 1200 ticks of survival play
  and 1 per ability use (at most once per 100 ticks). An absent `UNLOCKED` attachment means all 4 unlocked
  (creative, `/power set`).
- `unlockOrder()` (`P/ChronoPower.java:157`) = **R, V, B, G**: Rewind first, Overclock, Time Bloom, then Stasis
  last.
- `starterOnly` (0 unlocked, a natural awakening): only R is usable, and it runs **Nudge** in place of Rewind
  (`onKey` → `nudge`, `:331-339`). The Nudge cooldown is stored in Rewind's slot, so leftover Nudge cooldown carries
  over when Rewind unlocks.
- Every level-up (`onLevelUp` for mastery levels 2-4, and 5 = ascension through `Fate.ascend`) owes one echo
  (`ChronoEchoes.owe`). Unlocking the first ability (level 1) also calls `onLevelUp(1)`, so that owes an echo too.
- Mastery scales two things in code: the Stasis duration and the Chrono Punch damage. Both are effectively
  constant; see Rework notes.

### Starter: Nudge (R, while starterOnly)

`ChronoPower.nudge` (`P/ChronoPower.java:367`). By default it gives **you** Speed I for `NUDGE_TICKS`. While
sneaking, it gives Slowness I to the living entity under your crosshair within `NUDGE_RANGE` (block-clipped ray).
With no target, you get Speed I instead. It always succeeds, so the cooldown always starts.

| Constant | Value | Meaning |
|---|---|---|
| `NUDGE_RANGE` | 16.0 | crosshair reach (blocks) |
| `NUDGE_TICKS` | 80 | Speed I / Slowness I duration (4 s) |
| `NUDGE_COOLDOWN` | 300 | 15 s (Timeless doesn't shorten it) |

### Base abilities

Cooldowns come from `COOLDOWNS[]`. If `Fate.isAscended`, each is `round(cd / TIMELESS_COOLDOWN_RATE)`
(`cooldown()`, `:402`). Cooldowns start only if the ability returns `used = true` (`onKey`, `:321-355`).

| Constant (`ChronoPower`) | Value | Meaning |
|---|---|---|
| `REWIND_TICKS` (public) | 60 | rewind window (3 s); also sizes the history buffer (61) and `StasisField.DURATION` |
| `REWIND_MIN_HISTORY` | 10 | needs ≥ 0.5 s of history |
| `REWIND_COOLDOWN` | 300 | 15 s (Timeless 12 s) |
| `STASIS_COOLDOWN` | 600 | 30 s (Timeless 24 s) |
| `OVERCLOCK_TICKS` | 120 | 6 s |
| `OVERCLOCK_COOLDOWN` | 500 | 25 s (Timeless 20 s) |
| `OVERCLOCK_ATTACK_SPEED` | 0.6 | `ADD_MULTIPLIED_TOTAL` attack-speed modifier (×1.6), id `jaymospowers:chrono_overclock`, transient |
| `OVERCLOCK_DRAG_RADIUS` | 6.0 | Slowness aura radius |
| `OVERCLOCK_DRAG_AMPLIFIER` | 1 | Slowness II |
| `OVERCLOCK_DRAG_EVERY` | 10 | aura refresh period; each refresh lasts `EVERY + 10` = 20 ticks |
| `BLOOM_COOLDOWN` | 800 | 40 s (Timeless 32 s) |
| `BLOOM_HEAL` / `BLOOM_HEAL_EVERY` | 1.0 / 10 | self-heal 1 HP every 10 ticks while the bloom runs (10 HP = 5 hearts over 5 s) |
| `SYNC_INTERVAL` | 20 | HUD resync while anything is counting |

**R: Rewind** (`rewind`, `:453`). It fails, with no cooldown and an action-bar message plus a comparator click, if
there is less history than `REWIND_MIN_HISTORY` (`chrono_rewind_short`), if any sample is from another dimension
(`chrono_rewind_tangled`), or if no past spot fits your hitbox (`chrono_rewind_blocked`). Otherwise it picks the
**oldest** sample whose box has no collision, dismounts you, and teleports there with that sample's yaw and pitch.
It resets fall distance, puts out fire, and sets your health to the past value if that was higher (capped at max).
It adds `chosen` ticks to `ChronoMemory.stoppedTime` (progress toward ascension), clears the history, and starts a
`RewindTrail`. History: `ChronoState.record` runs every tick while alive, storing pos, yaw, pitch, health and
dimension in 61-slot ring arrays (`P/ChronoState.java:17-70`). History is cleared on death, spectator, gaining the
power, disconnect and after each rewind.
`RewindTrail` (`P/RewindTrail.java`): `PLAYBACK_TICKS = 6`. It replays the path as gold dust, end rods and sparks
with a rising amethyst chime, and ends with a bell and wax-on.

**G: Stasis Field** (`stasis`, `:507` → `P/StasisField.java`). The sphere is fixed at the cast position. Casting
again ends the old field first. `duration(player) = lerpInt(Mastery.unlocked/4, DURATION, DURATION_MASTERED)` (`:64`).

| Constant (`StasisField`) | Value | Meaning |
|---|---|---|
| `RADIUS` | 6.0 | sphere radius |
| `DURATION` | `REWIND_TICKS` = 60 | duration at 0 unlocked |
| `DURATION_MASTERED` | 120 | duration at 4 unlocked |
| `MAX_RELEASE_SPEED` (private) | 1.4 | cap on the stored knockback released when a mob unfreezes (blocks/tick) |

- It re-captures entities every 2 ticks (`capture`, `:152`). Freezable (`:170`): living `Mob`s except EnderDragon
  and WitherBoss; non-spectator `ServerPlayer`s except the caster (**allies included**); `Projectile`s in flight
  except FishingHook and FireworkRocket.
- There is one global `IdentityHashMap<Entity, Frozen> FROZEN` with a holder count, so overlapping fields work.
- **Mobs:** `ChronoLivingEntityMixin` cancels `LivingEntity.aiStep` at HEAD, server side, for non-players
  (`P/mixin/ChronoLivingEntityMixin.java:16`). Creepers are held at `swellDir = -1`. Hits still land; the knockback
  is stored in their delta movement and released, capped, when the field ends.
- **Projectiles:** velocity and NoGravity are saved, then motion is zeroed and NoGravity set. They are parked in
  place every tick and restored at the end.
- **Players:** `connection.teleport` back to the capture position every tick (they can still look around), with
  motion zeroed. `registerLocks` (`:70`) makes AttackEntity, AttackBlock, UseBlock, UseItem and UseEntity return
  FAIL, and BlockBreak BEFORE returns false, while frozen.
- While the field runs, `memory.stoppedTime++` every tick (`ChronoPower.tick`, `:285`).
- The field ends early on caster death or spectator mode, disconnect, a dimension change, `onLost`, or server stop.
- FX: a golden Fibonacci-sphere shell (48 points on start, 22 every 2 ticks, 60 on end), 12 "hour marks", a
  comparator tick-tock every 10 ticks, bell and beacon sounds.

**V: Overclock** (`overclock`, `:518`). Speed III and Haste II (`OVERCLOCK_TICKS`, ambient=false, visible=false,
icon=true), plus the transient attack-speed modifier. `state.overclockLeft` counts down in `tick`. Every
`OVERCLOCK_DRAG_EVERY` ticks, `overclockDrag` (`:534`) gives Slowness II to **every** living entity within 6 blocks
(players, pets and allies; ArmorStands excluded). `setOverclockModifier` runs every tick to reconcile the modifier.
`onLost` removes the effects only if they still look like ours (`removeOverclockEffect`, `:560`). FX: a wax-on spiral
and sparks, plus a click every second.

**B: Time Bloom** (`bloom`, `:585` → `P/TimeBloom.java`). The sphere sits at your block position. While it runs,
`ChronoPower.tick` also heals you (`BLOOM_HEAL`).

| Constant (`TimeBloom`) | Value | Meaning |
|---|---|---|
| `RADIUS` | 5 | sphere radius (blocks) |
| `DURATION` | 100 | 5 s |
| `RANDOM_TICK_CHANCE` | 0.3 | per candidate, per tick |
| `MAX_RANDOM_TICKS_PER_TICK` | 64 | budget |
| `EXTRA_BLOCK_ENTITY_TICKS` | 3 | extra ticks per block entity per tick (4× speed) |
| `MAX_BLOCK_ENTITY_TICKS_PER_TICK` | 60 | budget |
| `RESCAN_INTERVAL` | 20 | rescan of the sphere |

Liquids, ice, snow layers and WeatheringCopper are excluded from random ticks (`:122`). Spawner, TrialSpawner,
Vault, PistonMoving, EndGateway, CreakingHeart, CommandBlock and StructureBlock are never accelerated (`:130`). A
block-entity type whose ticker throws goes into the static `BROKEN` set for the rest of the session. Ticks run
through `EntityBlock#getTicker`, so modded ovens (Kitchen / Cooking for Blockheads) work without a compat class.

### Passives: habits, Echoes (the curse)

**Habits** go into `ChronoMemory` (persistent player attachment `jaymospowers:chrono_memory`, `copyOnDeath`, not
synced; `P/ChronoMemory.java:58`):
- **Mined:** `PlayerBlockBreakEvents.AFTER` in `init`.
- **Placed:** `ChronoBlockItemMixin` on `BlockItem.place` RETURN → `ChronoHooks.placed`.
- **Held:** main hand every `HELD_SAMPLE_INTERVAL` = 20 ticks.
- **Path:** every `PATH_SAMPLE_INTERVAL` = 40 ticks, on ground, not flying or riding, at least `PATH_MIN_STEP` = 3
  blocks from the last point (`remember`, `:409`).
- Lists cap at `MAX_KINDS` = 16 (the least-used kind is evicted) and `MAX_PATH_POINTS` = 96.
- Other fields: `echoesDue`, `returnsDue` (survival ticks), `stoppedTime`, `sighted`, `bond` (random, non-zero; it
  stamps the echoes).

**Echo spawning** (`P/ChronoEchoes.java`):

| Constant | Value | Meaning |
|---|---|---|
| `ECHO_DELAY_MIN` / `MAX` | 6000 / 30000 | an echo appears 5-25 min after a level-up |
| `RETURN_DELAY_MIN` / `MAX` | 1200 / 12000 | a shattered echo returns 1-10 min later |
| `SPAWN_MIN_DISTANCE` / `MAX` | 16 / 28 | ring around the owner |
| `SPAWN_ATTEMPTS` / `SPAWN_VERTICAL_RANGE` | 32 / 10 | spot search |
| `SPAWN_RETRY` | 200 | retry delay when no spot is found |
| `VIEW_DOT` | 0.35 | "in view" cone (spots out of view are preferred, with an in-view fallback) |
| `FOLLOW_RANGE` | 24.0 | echoes this close follow the owner through a dimension change… |
| `FOLLOW_MIN_DISTANCE` / `MAX` | 5 / 9 | …and arrive this far from them |
| `ORPHAN_CHECK_INTERVAL` | 100 | how often an online owner adopts orphans |

The timers count down only while the owner is online, alive and not in creative or spectator (`tick`, `:113`). A
shattered echo whose owner is offline is stored as an `Orphan` in the overworld's persistent attachment
`jaymospowers:chrono_orphans` (`ORPHANS`, `:86`). Dimension follow: `ChronoPower.tick` notices `lastLevel` changed →
`follow`. `onLost` → `fadeAll` + `ChronoMemory.forget` (bond dropped). Unloaded echoes fade later through the bond
check.

**Echo entity** `jaymospowers:chrono_echo` (`ChronoEcho extends Monster`, `MobCategory.MISC`, 0.6×1.8, tracking
range 8, no loot table, xpReward 12, persistent, never despawns far away):

| Constant (`ChronoEcho`) | Value | Meaning |
|---|---|---|
| `MAX_HEALTH` / `ATTACK_DAMAGE` / `MOVE_SPEED` / `AGGRO_RANGE` | 30 / 5 / 0.26 / 16 | attributes (the held item adds no damage) |
| `CHASE_SPEED` / `ROUTE_SPEED` | 1.2 / 0.9 | goal speeds |
| `MINE_CHANCE` / `PLACE_CHANCE` / `IDLE_SWING_CHANCE` | 300 / 700 / 140 | 1-in-N per tick (mine and place need `mobGriefing`) |
| `MINE_RADIUS` / `MAX_MINE_HARDNESS` / `MINED_KINDS` | 3 / 50 / 3 | mining reach (y 0..+2), hardness cap, owner's top kinds |
| `OWNER_CHECK_INTERVAL` / `SIGHT_RANGE` / `RELEARN_INTERVAL` | 20 / 32 / 2400 | bond check, first-sighting whisper, habit refresh |
| `ROUTE_POINTS` / `ROUTE_REACH` / `ROUTE_STEPS` / `ROUTE_PAUSE_MIN..MAX` | 48 / 48 / 8 / 40..140 | route walking (`WalkRoutesGoal`) |
| `TINT` | 0x9AB8F4FF | ARGB used by the renderer |

- **Targets:** players (its owner included), then any LivingEntity except echoes and ArmorStands (`registerGoals`,
  `:150`).
- **Outside the Nether:** any attributable hit or the void **shatters** it and queues a return (`hurtServer`,
  `:395`). Environmental damage is ignored.
- **In the Nether** it takes real damage. `die` → `ChronoEchoes.collapse` (`:290`), the permanent-death FX.
  `GENERIC_KILL` (`/kill`) always kills it.
- Saved NBT keys: `echo_owner`, `echo_bond`, `echo_profile`, `echo_mined`, `echo_placed`, `echo_route`.
- Client: shard dust and ticking sounds in `clientEffects`. `C/ChronoEchoRenderer.java` uses the owner's skin (tab
  list first, else the synced `ResolvableProfile` through `PlayerSkinRenderCache`), wide or slim model, and a
  flickering alpha (`FLICKER` 0.18, `FLICKER_SPEED` 0.13).

### Corruption, ascension and cure

- **Corruption** (no meter; `Fate.addCorruption` is never called for chrono): an echo killed **in the Nether** while
  its owner is online, holds Chrono and `Fate.canCorrupt` → `unravel` (`P/ChronoEchoes.java:328`). That plays a
  sonic boom and portal FX, gives the owner Blindness 30 t and Darkness 140 t, sends the chat message
  `chrono_echo_unravelled`, and calls `Fate.corrupt(player)`. If the owner is offline when the echo dies, nothing
  happens and the chance is lost.
- **Ascension (Timeless):** `stoppedTime` (stasis ticks plus rewound ticks) must reach `ASCEND_STOPPED_TICKS` =
  12000 (10 min). Then sleep long enough and wake while `level.isBrightOutside()` with `Fate.canAscend` (all 4
  unlocked) and no clock already in the inventory: the Stopped Clock is given (main hand, else inventory, else
  dropped) along with the whisper `chrono_clock_woke` (`sleep`, `P/ChronoPower.java:426`).
  `StoppedClockItem.use` (`P/StoppedClockItem.java:47`): with Chrono and `canAscend`, it shrinks the stack and calls
  `Fate.ascend`. Otherwise it plays a click. The item stacks to 1, is EPIC, and isn't in any creative tab.
- **Timeless effects:** base cooldowns ÷ 1.25 (`TIMELESS_COOLDOWN_RATE`). C becomes Chrono Punch. Client-side clock
  hands circle every Timeless player within 32 blocks (yours only in third person). There's no server-side form
  change.
- **Cure / purify: none.** Nothing in chrono calls `Fate.purify`, and `onPurified` isn't implemented. Corruption is
  permanent for chrono except through admin commands (`Fate.set` / `/power` commands).

### C: Chrono Punch (ascended ultimate)

`onUltimate` (`P/ChronoPower.java:224`) → `ChronoPunch.punch` (`P/ChronoPunch.java:48`). The cooldown is stored in
`state.ultimateReadyAt` and Timeless doesn't reduce it. Each tick, `ChronoPunch.tick` (END_SERVER_TICK) runs `hit`:
it targets living entities in a cone in front (excluding ArmorStands, allies and your own tamed pets) with line of
sight, clears their hurt-invulnerability and deals `playerAttack`. Punches 0-8 pin the target (velocity ×0.2,
Slowness V for 10 t). Punch 9 throws (`FINISH_PUSH` horizontal + 0.45 up, explosion particle). Each punch sets
`ChronoFlow.MOVE` to a `RAPID` move, which drives the alternating-jab pose.

| Constant | Value | Meaning |
|---|---|---|
| `PUNCHES` / `EVERY` | 10 / 1 | 10 punches, one per tick (0.5 s) |
| `REACH` / `CONE` | 3.5 / 0.45 | reach from the eye (+ half the target's width), cos of the half-angle (~63°) |
| `DAMAGE_BASE` / `DAMAGE_PER_LEVEL` / `RAMP` | 0.6 / 0.2 / 0.12 | per punch `(0.6 + 0.2·unlocked)·(1 + 0.12·n)` → 1.4…2.91, about 21.6 total |
| `FINISH_PUSH` | 1.3 | last punch's throw |
| `COOLDOWN` | 160 | 8 s |

### Z: Time Flow (Timeless and corrupted)

`onMode` (key down) → `ChronoFlow.toggle` (`P/ChronoFlow.java:244`). It needs `available` = ascended **and**
corrupted. Otherwise it shows `chrono_flow_unavailable`. `tickPlayer` (`:274`, called from `ChronoPower.tick`)
stops the mode if no longer available, dead or spectator. It drains `EXHAUSTION` per tick (not in creative), resets
the combo after `COMBO_TIMEOUT`, and adds gold shimmer particles.

- **Strikes:** the client's `ClientPreAttackCallback` (`C/ChronoFlowClient.java:58`) picks a target (`pick`, `:241`:
  within RANGE, cos ≥ `AIM_CONE` 0.55, line of sight, score `(1−aim) + 0.05·dist`). The style comes from `styleFor`
  (`:621`): above `MID` → KICK, above `CLOSE` → LUNGE, else JAB/HOOK/UPPERCUT cycling. The client faces the target,
  sends `Strike(target, style)`, and dashes (lunge 4 t, kick 7 t with hop 0.38, stopping 1.5 short). The client
  swallows clicks until `HIT_AT+1`. Server `strike` (`:311`) checks only "on, alive, style in JAB..KICK, target
  alive within RANGE+2". It sets `MOVE` and queues a `Blow` due at `HIT_AT[style]`.
- **Landing:** `land` (`:331`) needs the target within `HIT_REACH` (+ half its width). Damage =
  `(DAMAGE[style] + max(0, attackDamage−1)·WEAPON_SHARE) · (1 + min(COMBO_MAX_BONUS, combo·COMBO_BONUS))`. Blows
  1-3 of every 4 (and not counters) **stun** for `STUN_TICKS` and cancel knockback. Blow 4 (the finisher) and every
  counter push: KICK 1.1, LUNGE/COUNTER 0.7, others 0.35; the finisher multiplies by `FINISHER_PUSH`. Lift is
  UPPERCUT 0.55, KICK 0.3, else 0.12. The SWEEP and THROW counters override push and lift.
- **Stun:** a `STUN` attachment (game-time end), tracked in the static `STUNNED` list.
  - Mobs: `ChronoMobStunMixin` cancels `Mob.serverAiStep` and zeroes inputs.
  - Players: client-only `ChronoStunInputMixin` empties `KeyboardInput`. `Carry.canStruggle` also returns false.
- **Held blows / counters:** the `ALLOW_DAMAGE` hook `allowDamage` (`:397`) handles damage to a Flow player whose
  source entity is a LivingEntity **and** is the direct entity (melee only). If right-click was pressed ≤
  `PARRY_EARLY` ticks ago, the counter fires at once. Otherwise the blow is held for `PARRY_WINDOW` (one per
  attacker) with a white flash over the attacker and a bell only you hear.
  - Right click (client `keyUse` consumed each tick while not sneaking and no screen open; the `Parry` packet;
    server `parry`, `:430`) counters every held blow.
  - If the window passes, the blow lands and the combo resets to 0. Ending the mode lands every held blow
    (`release`).
- **Counter variants:** with one attacker, `counterOne` (`:483`) picks a variant at random from CIRCLE…THROW, plus
  a random deflect 0-4 and a side. The caster is untouchable until `counterUntil` (the move's duration). The
  attacker gets Slowness IV and Weakness II for `STAGGER_TICKS` and a stun until hit + `COUNTER_STUN_SLACK`. With
  several attackers, `counter` (`:459`) uses a CROWD counter: everyone staggered, a COUNTER blow on each, and **no**
  invulnerability window. Client movement for CIRCLE, SLIP, VAULT and THROW is in `counterMotion` (`:141`).

| Constant (`ChronoFlow`) | Value | Meaning |
|---|---|---|
| `RANGE` / `HIT_REACH` | 9.0 / 3.8 | target pick reach / landing reach |
| `CLOSE` / `MID` | 2.4 / 5.0 | distance bands for the style |
| styles | JAB 0, HOOK 1, UPPERCUT 2, LUNGE 3, KICK 4, COUNTER 5, RAPID 6 | `Move.style` |
| `DURATION[]` / `HIT_AT[]` | {6,7,8,9,12,10,3} / {2,3,4,5,8,4,1} | animation length / blow tick |
| `DAMAGE[]` | {1.5,1.75,2.0,2.0,2.5,2.5,0} | base damage per style (RAPID unused) |
| `COMBO_BONUS` / `COMBO_MAX_BONUS` | 0.12 / 2.0 | +12% per blow, up to triple damage |
| `WEAPON_SHARE` | 0.25 | share of the weapon's damage above 1 |
| `COMBO_TIMEOUT` | 80 | combo resets after 4 s with no blow |
| `PARRY_WINDOW` / `PARRY_EARLY` | 14 / 5 | hold time / early right-click grace |
| `STAGGER_TICKS` | 40 | Slowness IV + Weakness II on countered attackers |
| `STUN_TICKS` / `SET_SIZE` / `FINISHER_PUSH` | 15 / 4 / 1.3 | sets of four |
| variants | CROWD 0, CIRCLE 1, SLIP 2, DEFLECT 3, VAULT 4, SWEEP 5, THROW 6 | `Move.variant` |
| deflects | HIGH_BLOCK, OUTSIDE_SWEEP, SLAP_DOWN, CROSS_BLOCK, ELBOW_GUARD (`DEFLECTS` = 5) | `Move.deflect` |
| `COUNTER_DURATION[]` / `COUNTER_HIT_AT[]` | {10,24,20,26,24,22,26} / {4,17,12,18,16,13,17} | per variant |
| `COUNTER_STUN_SLACK` / `DEFLECT_SHOVE` | 3 / 0.25 | |
| `SWEEP_LIFT` / `SWEEP_PUSH` / `THROW_PUSH` / `THROW_LIFT` | 0.75 / 0.2 / 0.85 (negated: back over you) / 0.5 | |
| `EXHAUSTION` | 0.006 | hunger per tick (~1 food point per 33 s) |

Client-side constants (`C/ChronoFlowClient.java:25-40`): `AIM_CONE` 0.55, `DISTANCE_WEIGHT` 0.05, `LUNGE_TICKS` 4,
`KICK_TICKS` 7, `STOP_SHORT` 1.5, `KICK_HOP` 0.38, `CIRCLE_DELAY` 2, `CIRCLE_TICKS` 10, `CIRCLE_MIN/MAX_RADIUS`
1.6/2.6, `CIRCLE_MAX_STEP` 1.0, `SLIP_TICKS` 3, `SLIP_SPEED` 0.42, `SLIP_BACK` 0.12, `VAULT_DELAY` 3, `VAULT_TICKS` 8,
`VAULT_HOP` 0.5, `VAULT_LAND` 1.4, `VAULT_MAX_STEP` 0.8, `THROW_TICKS` 3, `THROW_CLOSE` 0.9. Combo HUD
(`renderHud`, `:267`): `hud.jaymospowers.chrono_flow_combo` above the crosshair from combo 2 up, brighter from 10.

**Poses** (`C/ChronoFlowPose.java`): `ChronoAvatarRendererMixin` (TAIL of `AvatarRenderer.extractRenderState`) →
`extract` (`:30`). It stores `Pose(move, progress)` under the `RenderStateDataKey` `jaymospowers:chrono_flow_pose`.
For the local player, the predicted `ChronoFlowClient.local` move wins when it's newer than the synced `MOVE`.
`applyModel` (`:44`) runs from the Web mixin after `setupAnim` and sets the poses outright, so applying them twice
is harmless: JAB/RAPID, HOOK, UPPERCUT, LUNGE, KICK, the CROWD counter (guard then palm), and `counter` (`:117`) for
CIRCLE, SLIP, VAULT, SWEEP, THROW and DEFLECT (`deflect`, `:239`, five parries).

### HUD (`C/ChronoPowerClient.java`)

- 4 dials (`FACE` 18 px, `GAP` 2) placed `width/2 + 91 + 30` from the left on the bottom row. If that overflows the
  screen, they move above the hotbar.
- A 5th square chip (Chrono Punch, `drawParadox`, `:228`) shows while ascended.
- Texture `chrono_hud.png` 64×32: dial idle at u=0, dial active at u=18 (v=0), glyphs 10×10 at v=18 with u = i·10
  (ult glyph u=40).
- Cooldown: a dark pie (`shadePie`) and a golden hand, with seconds shown when not active. Active effects get a
  gold bar above the dial. Overclock adds a pulsing golden vignette (`vignette`, `:338`).
- Flashes: ready (8 t outline and a chime), denied (6 t red outline and a click; client-side on a press during
  cooldown).
- Locked abilities: a dark slot with just the key. The starter R counts as usable, so it shows the Rewind glyph
  during Nudge.
- `ChronoSync` (S2C, `P/ChronoSync.java`): for each of 4 abilities, cooldownLeft/Total and activeLeft/Total, plus
  ultimateLeft/Total. It's sent on change and every 20 t while anything ticks. The client counts down between
  packets. `onLost` sends all zeros. `onActiveChanged` resets the client state.
- Timeless clock hands (`timelessHands`, `:97`): `TIMELESS_RADIUS` 0.85, `TIMELESS_HEIGHT` 1.05,
  `TIMELESS_VIEW_DISTANCE` 32, `TIMELESS_MINUTE_SPEED` 0.16 and `TIMELESS_HOUR_SPEED` 0.035 rad/tick,
  `TIMELESS_INTERVAL` 2. Colours: minute 0xFFE8A0, hour 0xA8E8FF.

### State, attachments, packets

| Id | Where | Kind | Notes |
|---|---|---|---|
| `jaymospowers:chrono_memory` | `ChronoMemory.MEMORY` | player, persistent, copyOnDeath, not synced | habits, timers, stoppedTime, bond |
| `jaymospowers:chrono_orphans` | `ChronoEchoes.ORPHANS` | overworld level, persistent | returns waiting for offline owners |
| `jaymospowers:chrono_flow` | `ChronoFlow.ON` | player, synced to all, **not persistent** | Time Flow on (lost on relog/death) |
| `jaymospowers:chrono_flow_stun` | `ChronoFlow.STUN` | any LivingEntity, synced to all | stun end (game time) |
| `jaymospowers:chrono_flow_move` | `ChronoFlow.MOVE` | player, synced to all | current move (`Move` record + STREAM_CODEC) |
| `jaymospowers:chrono_flow_combo` | `ChronoFlow.COMBO` | player, synced to the target only | combo count |
| `jaymospowers:chrono_sync` | `ChronoSync` | S2C payload | HUD |
| `jaymospowers:chrono_strike` / `chrono_parry` | `ChronoFlow.Strike` / `Parry` | C2S payloads | Time Flow |

In-memory server maps (all cleared on SERVER_STOPPING): `ChronoPower.STATES` (cooldowns survive death and relog,
not a restart), `StasisField.FROZEN`, `ChronoPunch.FLURRIES`, and `ChronoFlow.HELD`, `BLOWS`, `FIGHTERS` and
`STUNNED`. Static `TimeBloom.BROKEN` is never cleared.

Lang keys (`lang/parts/chrono.json`): `power.jaymospowers.chrono[.desc|.ascended]`, `.key.ability_1..4`,
`.key.starter`, `.key.ultimate`, `.key.mode.ascended_corrupted`, `message.jaymospowers.chrono_rewind_{short,tangled,blocked}`,
`chrono_echo_{sighted,collapsed,unravelled}`, `chrono_clock_woke`, `chrono_flow_unavailable`,
`hud.jaymospowers.chrono_flow_combo`, `entity.jaymospowers.chrono_echo`, `item.jaymospowers.chrono_stopped_clock[.lore1|.lore2]`.

### Doc/code mismatches

- **Lang `key.starter`** says "slow what you look at (sneak: speed yourself)". The code does the **reverse**:
  default speeds you, sneak slows the target. The 0.7 swap is in Abilities.md and the commit message, but the lang
  wasn't updated, and MODLOG line ~156 still has the old wording.
- **Lang `key.ability_2`** says Stasis lasts 3 s. In practice it's always 6 s (see the next section).
  `docs/chrono.md` "Controls" still says 4 s, mobs only (not players), and its tuning table has `DURATION` 80.
- `docs/chrono.md` still documents **Paradox Cut** (the 0.2.0 ultimate, `ParadoxCut` constants). The code has
  Chrono Punch since v0.6.4, and no `ParadoxCut` class exists. It also says "Chrono has no corruption meter / the
  echoes are its corruption". Corruption is now the Nether unravel. The `ChronoPower` class javadoc says the same.
- `ChronoFlow.counterOne` javadoc says "circle, slip or deflect (picked at random)". The code picks from all six
  (circle, slip, deflect, vault, sweep, throw).
- The `ChronoPowerClient` class javadoc says "client side: only the HUD". It also does the Timeless hands, the echo
  renderer and the Time Flow init.
- `docs/chrono.md` HUD says the duration bar is "under" the dial. The code draws it above (`fy - 3`).

### Rework notes (coupling, gotchas, bugs)

1. **Stasis mastery scaling is dead code.** Stasis is last in `unlockOrder`, so it can only be used at 4 unlocked →
   `duration()` always returns `DURATION_MASTERED` (120). `DURATION` / the lerp never matter, and the "3-6 s" in
   Abilities.md never happens.
2. **Chrono Punch `DAMAGE_PER_LEVEL` is effectively constant.** Ascension requires `canAscend` (4 unlocked), so
   damage is always `(0.6 + 0.8)·ramp`. "Stronger with mastery" is cosmetic.
3. **No cure path.** There is no `Fate.purify` caller for chrono and no `onCorrupted` / `onPurified`. If the rework
   adds a cure, also decide what happens to Time Flow (it auto-stops via `available()` in `tickPlayer`).
4. **Strike packet trust:** the server doesn't rate-limit `Strike` packets (only the client's `busyUntil`). It
   accepts any style JAB..KICK whatever the distance and doesn't check line of sight. A modified client could queue
   many blows per tick. `Parry` has no cooldown either, though the `counterUntil` guard stops overlapping counters.
5. **Player stun is client-enforced** (`ChronoStunInputMixin`). The server doesn't hold stunned players, unlike
   Stasis, which teleports them back.
6. **Held blows are melee only:** projectiles, explosions and magic go straight through Time Flow (`allowDamage`
   requires `source.getDirectEntity() == attacker`). The CROWD counter gives no invulnerability (`counterUntil` is
   only set in `counterOne`).
7. **Movement checks:** Time Flow dashes and counter motion are client-driven, and chrono doesn't override
   `skipsMovementCheck`. It has been fine so far, but a faster dash could trigger "moved too quickly".
8. **Friendly fire:** Stasis freezes allied players and Overclock's drag slows allies and pets. Chrono Punch
   excludes allies and pets. Echoes also attack their owner by design.
9. **Shared `ChronoFlow.MOVE`:** Chrono Punch writes `RAPID` moves into it even outside Time Flow, and
   `ChronoFlowPose` reads it for every avatar. Changing `Move` (fields, codec) changes a synced attachment's wire
   format, so bump the version. `Move.duration()` clamps the variant to CROWD..THROW, so any new variant must extend
   both `COUNTER_DURATION` and `COUNTER_HIT_AT`.
10. **Pose path is in the Web package:** `ChronoFlowPose.applyModel` is called from
    `WebModelFeatureRendererMixin`. If you move or rename it, edit that Web file too (a cross-track edit).
11. **Attachment registration:** any new chrono attachment holder class must be added to
    `JaymosPowers.registerAttachments()`. `ChronoPower.init` also "touches" `MEMORY` and `ORPHANS` with a debug log,
    which is redundant now. `ChronoFlow.ON` isn't persistent, so Time Flow silently turns off on relog or respawn
    (`FIGHTERS` entries linger harmlessly).
12. **Nudge shares Rewind's cooldown slot** (`readyAt[0]`). On the HUD the starter shows the Rewind glyph. Timeless
    doesn't affect Nudge (irrelevant in practice, since starterOnly players can't be ascended).
13. **Mastery credit** is granted by core `Powers.key` on any press of a usable key, even when chrono rejects it
    (cooldown, a failed Rewind). It's rate-limited to once per 100 ticks.
14. **Echo level-up count:** `onLevelUp` is also called when going 0→1 (the first unlock), so a natural awakening
    owes 4 mastery echoes plus 1 for ascension (5 total). Comments say "mastery 2-4, ascension 5".
15. **Corruption needs the owner online:** if an echo dies in the Nether while its owner is offline, `collapse`
    finds no player and doesn't corrupt them. That echo is gone for good, and the corruption chance with it.
16. **Echo is a `Monster`**, so it is probably removed on Peaceful difficulty without a queued return (not
    verified). `MobCategory.MISC` keeps it out of the monster cap.
17. **Cooldowns are in memory** (`STATES`, keyed by UUID, never pruned on disconnect). A restart resets them. The map
    keeps one entry per chrono player seen this session.
18. **`stoppedTime` overflow guard** only covers the Stasis increment. `rewind` adds `chosen` (≤ 60) without the
    guard. Negligible.
19. **Echo routing:** `ChronoMemory.pathIn` and `spansOtherDimension` compare `ResourceKey`s with `==`. That relies
    on interning, which holds for vanilla.
20. **Every autonomous effect** is server particles and sounds (`sendParticles`), so other players see them.
    Client-only visuals: the Timeless hands, the echo shard dust and ticks, the dash enchant particles, the HUD and
    the vignette.
21. **Debug command** shares the `powerdebug` root literal with the other powers' debug commands (Brigadier merges
    them). Keep the `chrono` sub-literal.

---

## Web Slinger (detailed)

This section describes the code on `master` at v0.8.22. Where the code and the docs disagree, the code wins.
Path shorthand: `P/` = `src/main/java/com/jaymospowers/power/web/`, `C/` = `src/client/java/com/jaymospowers/client/power/web/`.
Line numbers are approximate (±2).

**Size:** 28 server classes (about 9.7k lines) and 22 client classes (about 5.9k lines). The longest files are
`WebSymbiote.java` (2984 lines), `WebSwing.java` (927), `WebSymbioteClient.java` (1139), `WebPower.java` (996),
`WebTakeoverPath.java` (865) and `WebTendrils.java` (864). `WebSymbiote.tick` (P/WebSymbiote.java:840-1150) is one
310-line method.

**Who owns what.** Player motion is client-authoritative. The client runs swing, slingshot, wings, the charged leap,
the zip/yank-to reel, Wall Crawl movement and takeover steering. The server validates anchors, cancels fall damage
and the flying kick, decides Zip/Yank/Shot/hitch, and runs the whole symbiote (bond, hunger, takeover AI, tendrils,
feeding). The server moves the local player only by sending `WebNet.Nudge(velocity)`, which the client applies
with `setDeltaMovement` (C/WebSymbioteClient.java ~300).

### 1. Keys and what each one does now

| Key | Base (no symbiote) | Symbiote (suit / Living Armor) | Server entry | Client entry |
|---|---|---|---|---|
| **B** (ABILITY_4), starter | Starter-only: **webline** (hold on block A, release on block B) | Ropes (suit) or tendrils (Living Armor) instead of web | `WebPower.onKey` P/WebPower.java:638 → `startWebline` :704 / `finishWebline` :722 | none (server only) |
| **B** once unlocked | **Yank**: tap pulls in; hold = tether/lead; release aimed at a block = **hitch**; nothing under the crosshair = webline | Reach ×1.5 | `yank` :958, `tickTether` :754, `WebHitches.hitch` | HUD chip only |
| **R** (ABILITY_1) | Main-hand **swing** (hold) | Suit: black rope, range ×0.3 (tendril swing). Living Armor: **tendril grab/throw**; Sneak at a block: **rip a chunk** | `onSwing` :525 (validation only) / `WebTendrils.Grab` | `WebPowerClient.onKey` C/WebPowerClient.java ~150 → `WebSwing.press` |
| **G** (ABILITY_2) | Off-hand swing (needs an empty off hand) | as R, left tendril | same | same |
| **V** (ABILITY_3) | Tap (<20 t): **pellet**. Hold: **charged web blast** | Black "symbiote mass" (more damage) | `startShot` :896 / `shoot` :906 | charge bar in HUD |
| **Z** (mode) | **Web Zip** (unlocks with G) | Zip cooldown ×0.5, reach ×1.5 | `onMode` :350 → `zip` :824 | `WebSwing.startPull` on `Pull` packet |
| **C** (ultimate) | **Web Strike** (ascended only) | n/a | `WebAscension.ultimate` P/WebAscension.java:237 | chip |
| **Jump** (air) | **Web wings** (hold in air after a 2-block drop) | Suit: no wings; **charged leap** instead | `onWings` :562, `ChargeHold` packet | `WebSwing.tickWingsInput`/`tickChargeJump` |
| **Sneak + Jump** with both webs | **Slingshot** | Living Armor breaks the wall after 5 s | `WebStrain` | `WebSwing.tickSling` :536 |
| **X** (`key.jaymospowers.web_symbiote`) | none (it is Wind's Tempest Kick for Wind users) | **Radial menu** (6 abilities + suit mask) | `WebSymbiote.use` :1736 | `WebSymbioteScreen` |
| Punch a line | **Cut** any web line (1-4 punches) | n/a | `WebCut` | `WebCutClient` |
| LMB/RMB with things held | none | **Chunk smash** (cross), **combos** (clap/crush/shatter/thrash) | `WebTendrils.smash`, `WebTendrilCombos.attack` :209 | `ClientPreAttackCallback` in `WebPowerClient.init` |

The Z menu key text says "G is only the off-hand web". In `onKey` the server ignores R and G entirely: they are pure
client keys plus the `Swing` packet.

### 2. Progression

- **Mastery** (`core/Mastery`): `UNLOCK_AT = {0,20,60,120,200}` points. A natural awakening starts at level 0, with
  only the starter (B webline). Unlock order (`WebPower.unlockOrder` :287): **R, G, B (Yank), V**. Z (Zip) is gated on
  G (`slotUnlocked`, `onMode`). Creative and `/power set` unlock everything.
  - "Mastered" (`Mastery.unlocked == 4`) means: Yank and webline cooldowns are 0 (`yankCooldown`), and up to 5 hitch
    lines instead of 1 (`WebHitches.LINES/MASTERED_LINES`).
  - The server gates keys through `Powers.key → Mastery.isUsable` (core/Powers.java:~165). **The Swing packet itself
    is not mastery-checked on the server** (`onSwing` :525). Only the client gates R and G.
- **Ascension** (`WebAscension`): the server adds up how far you travel per tick while a swing web is stuck and you
  are airborne. Steps over `MAX_STEP` 6 are skipped, and the total is flushed every 20 t into the persistent
  `web_swung` (copyOnDeath, *not synced*). At 2000 blocks it calls `Fate.ascend`, if `Fate.canAscend` allows.
  Whispers come at 50% and 85%. **`onLost` deletes `web_swung`**, so swapping the power away resets the progress.
- **Corruption:** none. The symbiote is Web's dark side; its badge comes from `statusSuffix` (`.symbiote`, or
  `.symbiote_ascended` if ascended).
- **Symbiote bond:** 0-100. The start is random, 0-50 (`BOND_START_MAX`). Bond level gates radial abilities
  (`UNLOCK_BOND`, see §5).

### 3. Base kit: mechanics and constants

**Web Swing.** Client physics are in `WebSwing.tickSwing`, C/WebSwing.java:384. Anchors come from `WebAnchors.find`,
which is shared code: the client picks and the server checks.

| Constant (C/WebSwing.java:31-113) | Value | Meaning |
|---|---|---|
| `ATTACH_SLACK` | 1.0 | Rope starts at this × anchor distance (**docs say 0.94**) |
| `ROPE_MIN` | 3.0 | Shortest rope after reeling in |
| `ROPE_SPRING` / `ROPE_MAX_PULL` | 0.3 / 0.7 | Over-stretch spring per block, and its cap (b/t) |
| `SWING_GRAVITY` | 0.025 | Extra gravity on the web |
| `SWING_DRAG` / `PENDULUM_DRAG` | 0.993 / 0.996 | Velocity kept / energy kept per tick |
| `PUMP` / `JUMP_THRUST` | 0.03 / 0.07 | W on the downswing; Jump thrust along the arc |
| `REEL_IN` / `LET_OUT` | 0.45 / 0.35 | Rope change per tick (Jump / Sneak) |
| `MAX_SPEED` | 2.6 | Speed cap, b/t |
| `RELEASE_SWEET_ANGLE` / `RELEASE_WINDOW` | 35° / 35° | Boost window on release |
| `RELEASE_FORWARD` / `RELEASE_UP` | 0.35 / 0.5 | Release boost at full quality |
| `FLIGHT_DRAG` | 0.985 | Horizontal drag after release until landing |
| `TAUT_SLACK` | 0.15 | Rope counts as taut within this |
| `RETRY_TICKS` | 3 | A held key in the air re-tries an anchor this often |
| `HANG_SPEED` / `HANG_HOLD` / `HANG_TICKS` | 0.12 / 0.75 / 8 | Apex hang: 75% of gravity refunded for 8 t near the apex |
| `ROPE_HEIGHT` | 1.2 | Rope attaches at hand height |

| Anchors (P/WebAnchors.java:19-31) | Value |
|---|---|
| `SWING_RANGE` / `ASSIST_RANGE` | 48 / 40, both × `WebSymbiote.swingScale` (1.5 bonded and bare, **0.3 with the suit shown**) |
| `DIRECT_MAX_DROP` | 4 (a crosshair hit lower than this below the eyes falls back to an assisted anchor) |
| `ASSIST_ELEVATIONS` / `ASSIST_YAWS` | {64,54,44,74,34,24}° / {0,±12,±25,±40}° |
| `ASSIST_BEST_ELEVATION` / `_DISTANCE`, `ASSIST_MIN_HEIGHT` / `_DISTANCE` | 52° / 20, 3 / 6 |

- **Server** (`onSwing` :525) checks `WebAnchors.valid`: within `SWING_RANGE*reachScale + 4`, `nearSolid` (a block
  with collision within 0.5), and line of sight with 2.5 tolerance. If any check fails it sends `SwingReject(hand)`.
  On success it sets the `web_line` / `web_line_off` attachment, sets `grace`, and plays the thwip for others.
- Every 10 t, `WebPower.tick` (:431) drops the swing if the anchor is no longer `nearSolid`, or if the off hand
  became occupied.
- **Float passive:** `GRAVITY` ×0.8 (`FLOAT_GRAVITY = -0.2`, `web_float`), reapplied every 20 t by `ensurePassive`.
  The old `web_safe_fall` modifier is now only *removed* (the safe-fall passive is gone).
- **Fall damage applies** outside creative. "Grace" now only makes `allowsFloating` and `skipsMovementCheck` true
  (`inGrace`) and caps `fallDistance` at 1 during a wing glide slower than 0.5 b/t. `GRACE_MAX_TICKS` = 1200.
  **The docs' "no fall damage while swinging" (web.md Controls, Abilities.md line 86) is stale.** One exception:
  the suit or Living Armor cancels *all* fall damage (`WebSymbiote.register` ALLOW_DAMAGE, P/WebSymbiote.java:638).

**Slingshot** (both webs + Sneak brace, Jump draw; `WebSwing.tickSling`/`fireSling` :536/:604).
`SLING_DRAW_TICKS` 30, `SLING_MIN/MAX_SPEED` 0.9/3.4, `SLING_AIM` 0.8, `SLING_LOCKOUT` 12, `SLING_MIN_DRAW` 0.12,
`SLING_ARMOR_BREAK_TICKS` 100, `STRAIN_TICKS(_ARMOR)` 60.
- While drawing, the client sends `WebStrain.Strain(progress, behind)`. The server cracks blocks up to `ALMOST` 0.9
  (hardness ≤ `MAX_HARDNESS` 3.0, or 5.0 armored). At progress 1 with Living Armor it `destroyBlock`s a 3×3.
- If the floor goes, `WebPower.snapLines` drops both lines, and the `WebPose.RAGDOLL` pose lasts 40 t.

**Web wings** (`tickWingsInput`/`tickWings` :729/:761). Hold Jump for `WINGS_HOLD_TICKS` 6 after falling
`WINGS_MIN_DROP` 2 from the apex. They glide elytra-style (`WINGS_GRAVITY` 0.08, `WINGS_MAX_SPEED` 2.0), lasting
`WebPower.WINGS_TICKS` 80 on the client.
- The server gives Feather Falling: `FALL_DAMAGE_MULTIPLIER` −48% (`WINGS_FEATHER_FALLING`), lingering 10 t.
- The server folds the wings only on ground or water. **`WINGS_COOLDOWN` (60) is unused**, and the comment on
  `WINGS_TICKS` saying "the server folds them itself" is false.

**Charged leap** (suit shown, or a takeover; `tickChargeJump` :679, `WebLeap`). `CHARGE_FULL` 30 t, up
0.45-1.65, forward ≤1.1. `WebJumpMixin` cancels the vanilla jump while charging. The `ChargeHold` packet sets
`state.chargeHold`, which resets `fallDistance` every tick (fall immunity while held) and shows the synced
`web_charging` crouch.

**Web Zip (Z)** (`zip` :824; client `tickPull` :821).
- `ZIP_RANGE` 48 × reachScale, `ZIP_SPEED` 1.8, `maxTicks = dist/1.8 + 12`. Cooldown `ZIP_COOLDOWN` 60 (30 bonded).
- Client steering: `ZIP_STEER` 0.55. It lets go `ZIP_RELEASE_DISTANCE` 4 short with `ZIP_RELEASE_LIFT` 0.55,
  unless you hold Sneak (then it perches, `PERCH_FORWARD` 0.22).
- It aborts if stuck 3 t. The server's `startPull` (:882) clears both swing flags and keeps `pullTicks = max+5`.

**Web Shot (V)** (`shoot` :906, `WebBall`, `WebTraps`).

| Constant | Value | Meaning |
|---|---|---|
| `SHOT_TAP_TICKS` / `PELLET_COOLDOWN` / `PELLET_SPEED` | 20 / 4 / 3.6 | Release before 20 t = pellet |
| `PELLET_DAMAGE` / `DARK_PELLET_DAMAGE` | 1.5 / 2.5 | Pellet direct hit |
| `SHOT_CHARGE_TICKS` / `SHOT_SPEED` | 60 / 2.4 | `charge = held/60`, so a blast is always ≥0.33 charged |
| `SHOT_COOLDOWN` → `SHOT_COOLDOWN_MAX` | 40 → 160 | lerp by charge |
| `DAMAGE_MIN/MAX`, `DARK_DAMAGE_MIN/MAX` | 0.5/10, 2/18 | Direct hit; splash takes up to half |
| `SPLASH_MIN/MAX` | 1.75 / 4.5 | Splash radius |
| `SLOWNESS_AMPLIFIER`, `SLOWNESS_MIN/MAX_TICKS` | 2 (III) / 60-220 | Splash snare |
| `SCATTER_MIN/MAX` | 1 / 9 | Loose cobwebs (more on weak shots) |
| `GRAVITY` / `LIFETIME` | 0.012 / 100 | Ball flight |
| `WebTraps.ENSNARE_TICKS` / `_SLOWNESS` / `COBWEB_TICKS` | 80 / 3 / 100 | Default snare and temporary cobweb |

The "dark" version (suit or ARMOR flag) uses `SYMBIOTE_MASS_ITEM` and `web_symbiote_web` black webs.

**Yank (B)** (`yank` :958, `pullIn` :939, `tickTether` :754).
- `YANK_RANGE` 24 × reachScale. `YANK_COOLDOWN` 120 (0 when mastered). A target with volume > `YANK_HEAVY_VOLUME`
  2.6 pulls *you* to it instead (a `Pull` with the entity id).
- A light target gets ballistic `pullIn`, then a tether: `TETHER_LENGTH` 3, `TETHER_SPRING` 0.25, `TETHER_MAX_PULL`
  0.9, `TETHER_BREAK` 4.5. Players get `PLAYER_TETHER_SPRING` 0.08 / `_MAX_PULL` 0.22 and snap free after 30 t
  strained past +1.5.
- `YANK_TAP_TICKS` 6: a release this soon is a tap (no hitch). `TETHER_SETTLE_TICKS` 20: no snap while the pull
  brings the target in.
- The tether snaps if the held target attacks anything (AFTER_DAMAGE in `init`), or the target swings (players).
- Synced state: `web_tether` on the caster (entity id) and `web_held` on the target, which makes clients ragdoll it.

**Hitches** (`WebHitches`, P/WebHitches.java). Release Yank aimed at a block within `HITCH_REACH` 24 ×reach.
- `MIN/MAX_LENGTH` 1/3 (2 from a ceiling), `SNAP_OVER` 1.5, `MAX_TICKS` 1200, `SPRING` 0.3, `MAX_PULL` 0.9.
- Mob movement is ×0.15 (`OWN_MOVEMENT`); players ×0.6, spring 0.08, and they break free after 30 t strain or one
  swing.
- Lines per slinger: 1 (5 mastered). More than `WEBBED_AT` 2 lines on one creature ensnares it.
- State: a static `HITCHES` list (server memory, not saved) plus the synced `web_hitch` `List<Line>` on the creature
  and a transient `web_hitched` movement-speed modifier.

**Webline (starter B)** (`WebBridges`). `REACH` 24 ×reach, `MAX_LENGTH` 24, `BRIDGE_TICKS` 1200, `COOLDOWN` 160 (0
when mastered; the cooldown shares the Yank slot). `WebBridgeBlock` planks (`height` 1-16, `style` WEB/ROPE/TENDRIL)
fill air only. The ends sync through the level attachment `web_weblines`. Planks are removed on tick expiry and on
`SERVER_STOPPING`.

**Cut lines** (`WebCut`). Anyone can do it. `REACH` 4.5, 1-4 punches rolled per line, remembered for 200 t. The
client `WebCutClient` finds the line; the server checks it and calls `WebPower.cutLine` / `WebHitches.cut` /
`WebBridges.cut`.

**Swing tricks** (`WebTricks` + `C/WebTrickClient`). The client picks a flip, twirl or landing; the server checks
`MIN_GAP_TICKS` 4 and that you are not dodging or ragdolled, then sets the synced `web_pose` (`WebPose`
5..`WINGS_LAND`).

### 4. Ascended: Spider-Sense and Web Strike (P/WebAscension.java)

| Constant | Value | Meaning |
|---|---|---|
| `ASCEND_DISTANCE` / `MAX_STEP` / `FLUSH_TICKS` | 2000 / 6 / 20 | Ascension tally |
| `SENSE_RANGE` / `SENSE_TICKS` | 16 / 10 | Server sends `WebNet.Sense(ids)` of mobs targeting you |
| `DODGE_CHARGES` / `DODGE_COOLDOWN` | **5** / 400 | 5 independent charges (**web.md says "first hit every 400 t"**; Abilities.md is right) |
| `DODGE_SIDESTEP` / `DODGE_HOP` | 0.85 / 0.22 | Dodge nudge; pose = one of SPIN/BACKFLIP/ROLL/WEAVE |
| `STRIKE_RANGE` / `STRIKE_COOLDOWN` | 32 / 160 | Web Strike (C): a Yank-to pull at a living target |
| `STRIKE_MIN_DAMAGE` / `_PER_SPEED` | 10 / 8 | `max(10, best speed of last 3 t × 8)` |
| `STRIKE_KNOCKBACK` / `STRIKE_LIFT` / `STRIKE_REACH` | 2.2 / 0.55 / 2.2 | The kick lands within 2.2 of the target's edge |

- The dodge is `ALLOW_DAMAGE` (`allowDamage` :187). It applies to any entity-sourced damage, including allies' and
  your own symbiote-related damage from others, but not to bypasses-invulnerability damage.
- The client red outline lives in `WebSymbioteClient.senseColor` (`SENSE_COLOR` 0xFFE02424, stale after 30 t),
  applied in `WebEntityRendererMixin`. The client adds swelling creepers and drawn bows itself.

### 5. The symbiote (P/WebSymbiote.java unless noted)

**Getting it.**
- Meteor craters: `WebCraterFeature` (radius 3.5-5.5, depth 3). Its placed feature is added to Overworld land
  biomes in `WebPower.init`.
- Sneak + right-click the `web_symbiote_goo` as a Web user (`onUseBlock` :1199) → `bond` :1227.
- Or bond with a roaming `WebSymbioteBlob` (`BOND_REACH` 1.4). Blobs come from dead hosts or meteors:
  `METEOR_CHANCE` 0.2, alive 30% of the time; otherwise the meteor leaves goo.
- `/powerdebug web symbiote bond|level|reject|feed`, `/powerdebug web swung`, `/powerdebug web crater`.

**Bond meter** (`WebSymbioteState` record: `bonded, satiety, stress, rejections, flags(SUIT=1, ARMOR=2, CRAWL=4,
BULK=8), hints, bond`). Persistent, copyOnDeath, synced to all.
- `UNLOCK_BOND` (radial order Tendril, Armor, Crawl, Consume, Spikes, Shroud) = {15, 60, 30, 45, 75, **0**}.
- `BOND_GAIN_TICKS` 2400: +1 when fed and stress < `BOND_CALM` 50; −1 when starving.
- `BOND_MEAL` +2; `BOND_EXPOSURE` −3; `BOND_BREAK_FREE` −2.
- `IGNORE_MAX` 0.45 → 0 at `IGNORE_BOND` 60: the chance a radial pick is ignored (a 20 t mini-cooldown).
- `SUIT_ABILITIES` 4 unlocks are needed before the mask brings the suit back (`suitAllowed`). `SUIT_DEBUT_TICKS` 100
  after `BOND_TICKS` 50.
- At bond 100, Living Armor also brings BULK (the "big body is yours to drive").
- Bonded perks: `REACH_SCALE` 1.5, `PULL_SCALE` 1.25, `ZIP_COOLDOWN_SCALE` 0.5, `TENDRIL_SWING_SCALE` 0.3 (the suit
  shortens swings).

**Radial menu** (C/WebSymbioteScreen.java, X; it releases on key-up via `InputConstants.isKeyDown`, and
mouse-button bindings fall back to clicking). It sends `WebNet.SymbioteUse(i)`; index 6 is the suit toggle.
`use` (:1736) checks, in order: sulking (suit/armor), bonded, not in a takeover or bond animation, unlocked,
cooldown, satiety ≥ `COST`, then the ignore roll.

| # | Ability | `COOLDOWN` (t) | `COST` | Implementation and numbers |
|---|---|---|---|---|
| 0 | Tendril Grab | 100 | 2 | `letGoOfWall` (if latched on a wall) else `WebTendrils.radialGrab` (`RADIAL_GRAB_RANGE` 20, auto-throw after `RADIAL_HOLD_TICKS` 50) else `tendril` :1835: a pull to a block (wall grab, `WALL_HOLD` 50, `WALL_CRACKS` 7, synced `web_symbiote_wall`) or a fling of an entity; above `TENDRIL_HEAVY_VOLUME` 8 it pulls you |
| 1 | Living Armor | 200 (from when it withdraws) | 0 | `toggle(ARMOR)` :1904. Put away after 10 t (`ARMOR_TOGGLE_GAP`); auto-withdraw after `ARMOR_IDLE_TICKS` 100 unused (suit off) or `ARMOR_MAX_TICKS` 1200 |
| 2 | Wall Crawl | 20 | 0 | toggles CRAWL; movement is client `tickCrawl` (C/WebSymbioteClient.java:884); server `allowsFloating` |
| 3 | Consume | 160 | 0 | `consume` :2762 (see Feeding) |
| 4 | Spike Burst | 240 | 8 | `spikes` :2918: `SPIKE_RADIUS` 5, `SPIKE_DAMAGE` 8, `SPIKE_KNOCKBACK` 1.3 (+0.45 up) |
| 5 | Shroud | 600 + 400 | 5 | `shroud` :2956: Invisibility for `SHROUD_TICKS` 400; any attack you make ends it (AFTER_DAMAGE) |

**Living Armor / big body (BULK).**
- BULK gives `ARMOR_ABSORPTION` 16 max-absorption, +1 every 40 t (`ARMOR_REGEN(_TICKS)`), knockback resistance 0.6,
  and `SCALE` +25% (`ARMOR_SCALE`; `applyArmor` :1923).
- Hunger drains ×3 (`ARMOR_DRAIN_MULT`).
- BULK is on exactly while a takeover drives, or with ARMOR at full bond (`bulkWanted`, ~:1125).
- Tendril strikes (`tickStrikes` :2570, synced `web_symbiote_strike`) run only while ARMOR or BULK is set:

| Strike | Constants |
|---|---|
| Normal | `STRIKE_EVERY` 50, `STRIKE_RANGE` 5.5, `STRIKE_TICKS` 40, hit at 20, `STRIKE_DAMAGE` 6; slam push 0.3 / lift 0.05 / airborne −0.45; swipe 0.7 / 0.25 / 0.25 |
| Heavy (holding debris) | `HEAVY_STRIKE_RANGE` 8.5, 60 t (hit at 35), every 90, 11 dmg × (0.6+0.1/piece, clamp 0.7-1.5), knock 1.0 / lift 0.45, shake 1.6/2.5 |
| Frenzy (takeover fight) | `FRENZY_TICKS` 18, hit 9, every 4, `FRENZY_DAMAGE` 9, `FRENZY_SNATCH` 0.18 |
| Retaliate (hit while armored) | `RETALIATE_LAG` 6, `RETALIATE_COOLDOWN` 25 (lash ≤ 6.5, else snatch ≤ 20) |

- Living Armor also catapults you while sprinting: `INSTINCT_CATAPULT_CHANCE` 0.05, ×1.4.

**Tendrils on R/G** (P/WebTendrils.java). Only with ARMOR or BULK (`armored`). The suit alone only swings.
- `GRAB_RANGE` 9 (server allows +2.5). The client aim inflates boxes by 0.7 and the crosshair goes red.
- Hold: `HOLD_FORWARD` 1.9, `HOLD_DROP` 0.45, sides 1.1-1.6, `TENDRIL_SPEED` 0.9 b/t reach, `HOLD_STEER` 0.45,
  `HOLD_MAX_SPEED` 1.4, `MAX_HOLD_TICKS` 300.
- Held targets are weightless (`GRAVITY` −100% modifier `web_tendril_hold` for living, `setNoGravity` otherwise).
- Throw: `THROW_SPEED` 2.4 + `THROW_LIFT` 0.25. Impact `max(4, speed×6)`; bowling hits `speed×5` and slow the body
  by 0.7. The `THROWN` static list lasts 40 t.
- **Chunk:** Sneak at a block → `rip`/`tickTugs`. Up to `RIP_PIECES` 6 blocks of hardness ≤ `RIP_HARDEST` 4.
  Tug time = 20 + 8×hardness + rand 16, capped at 60. Releasing early cancels. Needs `mayBuild`.
- **Cross smash** (click with a chunk): windup 22, swing 5, follow 8, recover 18; `CROSS_REACH` 5; dmg 6 + 1.5/piece
  (≤16).
- **Combos** (`WebTendrilCombos`, LMB with things held; `REAR` 12 / `HIT` 18 / `TICKS` 50):

| Combo | Damage and constants |
|---|---|
| Clap (two creatures) | 12 |
| Crush (debris + creature within 6) | 15 × size, Slowness 30 t |
| Shatter (two debris) | 10 (falloff 0.4) in a 6-block, 30° cone, size ≤ 1.8 |
| Thrash (corpse) | 1-4 slams, splash 5 within 1.6; butchers via `KitchenCompat.butcherRough` |

- Synced: `web_tendril_grips` (ids), `web_tendril_combo`, `web_tendril_climb` / `web_tendril_tug` (Latch).
  `HOLDS`, `TUGS`, `CROSSES` and `THROWN` are static server maps.

**Wall Crawl.** The client's `tickCrawl` (climb constants C/WebSymbioteClient.java:127-135: `CLIMB_REACH` 2.3,
pull steer 0.32 / max 0.42, `CLIMB_REACH_TICKS` 3 / `PULL_TICKS` 9, sag −0.03, `LEDGE_HOP` 0.35) sends
`WebNet.ClimbLatch`. The server checks the latch is ≤ `CLIMB_LATCH_REACH` 4.5 from the eyes, and `clinging` resets
fall distance (~:1024).

**Feeding** (never eats anything living; stages `FEED_KILL`→`GRAB`→`CARRY`→`FEED`, `tickFeeding` :2401).
- **Consume** (:2762) tries, in order:
  1. Eat the carried corpse.
  2. Grab a free corpse within `CONSUME_CORPSE_RANGE` 6.
  3. Lash a huntable mob within `CONSUME_RANGE` 3 that has ≤ `CONSUME_MAX_HEALTH` 10 HP (`CONSUME_FINISH_LAG` 8,
     `CONSUME_FINISH_DAMAGE` 100), then grab its corpse.
- **Self-feed:** when satiety < `SELF_FEED_BELOW` 30 (or `craving`), every `SELF_FEED_EVERY` 20 t: eat a carried
  corpse, or grab one within `SELF_FEED_RANGE` 14, or hunt prey (a takeover with `hunting`), then the corpse.
- Stage timers: `KILL_TICKS` 400, `CORPSE_WAIT_TICKS` 60, `CORPSE_FIND_RADIUS` 3, `GRAB_HAUL_TICKS` 8,
  `GRAB_TIMEOUT` 80, `CARRY_TIMEOUT` 20.
- **Meal** (`startEating` / `tickEating` :2824/:2842, synced `web_symbiote_eating`):
  - Timing: `EAT_REACH` 14, `EAT_BITE_GAP` 12, `EAT_CHEW` 40, `EAT_SHRINK` 8; two bites at size ≥ `BIG_MEAL` 0.5.
  - Heal 6-16 (`CORPSE_HEAL_MIN/MAX`); feed `CONSUME_FEED` 30 → `CORPSE_FEED_MAX` 80; calm 10; +2 bond.
  - Your own food gets `heal×0.75` at saturation 0.6.
  - The food must stay `KitchenCompat.carriedCorpse(player)` the whole time, or the meal aborts.
- **Without Jaymo's Kitchen, `carriedCorpse`/`leavesCorpse`/`carry` are all false**, so Consume and self-feed can
  never complete. Raw meat (`feedOnMeat` via `WebFoodPropertiesMixin`, 5 satiety per nutrition) is then the only food.
- Won't eat (`inedible`): skeletons, creepers, endermen, player corpses.
- Every whisper and the whole feeding darken the screen (`WebNet.Dread`, `WHISPER_FX_TICKS` 60, `FEED_FX_EVERY` 40).

**Hunger, stress, moods.**
- Satiety 0-100: starts at 70, −1 per 240 t (80 t with BULK).
- ≤ `STARVING` 15: it drains your food (`STARVE_EXHAUSTION` 1.2/40 t, ×2 at 0), and stress +1 per 80 t. Otherwise
  stress −1 per 100 t.
- Stress bands (stress/25) whisper `stress_1..4`.
- Your own food at 0 → `sulk` (:2536): it strips the suit and armor for
  `1200 × (1 + 0.5×rejections) × (0.5 + stress/100)` t (persistent `web_symbiote_sulk`, synced to the owner).
  **`rejections` is actually the exposure counter** (see Weaknesses), not times rejected.

**Instinct** (`instinct` :1955, every 10 t, not during takeover or bonding, not creative). It acts on, in order:
- A fall: catch it within `INSTINCT_FALL_CATCH_HEIGHT` 4, once per fall.
- A blast within 7: shove 1.25 + 0.45 up.
- Fire: clear the fire, shove 0.9.
- An attacker within 3: 50% snatch, else shove 0.95.

`INSTINCT_COOLDOWN` 30. Outside the armor it only pops `web_symbiote_tendrils_out` for 30 t. **It never raises
Living Armor itself (MODLOG 0.4.6 says 45% does; that is stale).** `fleeFire` (:2141) runs separately every 5 t
(shove at most every 12 t, 16-direction search).

**Takeover / frenzy** (`tick` ~:1034-1066, `startTakeover` :1458, `tickTakeover` :1669, path
P/WebTakeoverPath.java). A check runs every `TAKEOVER_CHECK_TICKS` 140, when not feeding and not creative.
`TAKEOVER_COOLDOWN` 900 is set at start. The first matching roll wins:

| Roll | Condition | Chance |
|---|---|---|
| Rampage | armor worn > `ARMOR_RESTLESS_TICKS` 800 and bond < 100 | `(armorTicks−800)/1200 × 0.2`, capped at 0.6 (**MODLOG says 12%/min**); targets players too; every 3rd player hit is a bite +8 (**it no longer heals or feeds, despite the constant's comment and MODLOG 0.4.4**) |
| Feral | bond < `FERAL_BOND` 60, not sulking | `0.12 × (1 − bond/60)`; 30% curious |
| Hunger | starving and stress ≥ 40 | 0.55 |
| Curious | fed, stress < 20, satiety ≥ 30 | 0.06: grab, stare 40-160 t, 50% attack |

- A takeover lasts `TAKEOVER_LIMIT` 1200, or until the prey dies or gets `TAKEOVER_ESCAPE` 30 away. Attack range is
  `TAKEOVER_REACH` 3, every 20 t; ending relieves stress by 20.
- Prey: mobs (not tame, not named) within `TAKEOVER_RANGE` 12; players only in rampage or curious mode.
- **Defend / last ditch** at ≤ `DEFEND_HEALTH` 6 with a threat (cooldown 1200): a panic line, Spike Burst, Shroud,
  then flee for `RUN_TICKS` 300 until `FLEE_SAFE` 24.
  - It leaps away (`LEAP_SPEED` 0.9, `LEAP_UP` 0.55), stress +15.
  - It hardens (+20 absorption) after `EMERGENCY_HITS` 2, or if stuck 100 t within 6 of the threat.
- **Desperate** at ≤ 10 HP: every 20 t, spikes plus a lash or snatch.
- Every takeover engulfs you: the suit is forced on and restored afterwards.
- **Break free:** possible after `BREAK_FREE_ABILITIES` 3 unlocks. It takes `36 − 4×unlocked` presses (decaying
  0.12/t) of any key, sent as `WebNet.Struggle`; this costs +10 stress and −2 bond.
- **Path:** server A* (`MAX_NODES` 1600, radius 32, repath every 10 t or when the target moves 3; leap rises from
  `WebLeap`). It sends `WebNet.TakeoverPath(nodes ≤ 48, mode PATH/DIRECT/RIP/FALLBACK)`.
  - Stuck for 30 t → it rips blocks (hardness ≤ 20, `mayInteract`, no block entities) as `BlockCluster` clumps and
    flings them.
  - The client follows the path (`TAKEOVER_SPEED` 0.38, `WALK_SPEED` 0.24, turn 0.35) and swallows input
    (`WebTakeoverInputMixin`, `WebSprintMixin`, `inputLocked`).

**Weaknesses** (`expose` :1401).
- Fire: one exposure per burning episode (episode gap 60 t), or any fire effect near the suit/armor via
  `core/Flames → nearFlames`.
- Noise (`WebServerLevelMixin` → `onGameEvent` :1359): explosion 16, horn 24, note block 8, bell 16. After one noise,
  more noise doesn't count for 100 t.
- Each exposure: 3 magic damage, Nausea, Slowness II, stress +20, bond −3, a vignette (fire 0xFF4A10, noise
  0xCFE4FF, sent through Wind's `ScreenFx`).
- The 6th exposure (`REJECT_AT`) calls `reject` :1251; the 5th gives the one-time "edge" warning.
- Flint and steel or a fire charge in hand (`flint`/`panic` :2196): the use fails, stress +10, then one of: drop the
  item, lash the nearest (4 dmg, else 2 to you), or bolt.
- Death: `withdraw` :1290 always ends the bond (it keeps hints and the exposure count) and spawns a
  `WebSymbioteBlob`. A blob that reaches a Kitchen zombie-player corpse turns it into a `WebBehemoth`.

### 6. Client side map

| Concern | Where |
|---|---|
| Swing, sling, wings, leap, zip reel, flight drag | `C/WebSwing.java` (state machine in the `WebPowerClient.INSTANCE.swing` singleton) |
| Keys, tendril grab aim, HUD (reticle, chips right of the hotbar) | `C/WebPowerClient.java` (`onKey` ~150, `renderHud` ~230). HUD row: wings, G, V, B, Z, C |
| Lines (own from local state, others from `web_line`/`web_line_off`/`web_tether`/`web_hitch`/weblines) | `C/WebLineRenderer.java` (`LevelRenderEvents.COLLECT_SUBMITS`, :206) |
| Arms reaching along webs, zip haul | `C/WebArms.java` (END_CLIENT_TICK for every player; `applyModel` :467) |
| Whole-body poses (dodges, tricks, ragdoll) | `C/WebBodyPose.java`; ragdolled mobs on lines `C/WebRagdoll.java` |
| Pose plumbing | `WebModelFeatureRendererMixin.prepareModel` (after `setupAnim`), `WebLivingEntityRendererMixin`, `WebAvatarRendererMixin` |
| Suit, growth, maw, tendrils, feral stance | `WebSymbioteLayer`, `WebSuitGrowth`, `WebMouthLayer`, `WebTendrilLayer`, `WebFeral` (layers registered in `WebSymbioteClient.init`) |
| First person | `WebSymbioteHand` + `WebFirstPersonHandsMixin`; `FirstPersonCompat` (reflection on tr7zw FPM) |
| Radial, takeover steering, crawl, Spider-Sense, nudge/shake/dread receivers | `C/WebSymbioteClient.java`, `WebSymbioteScreen`, `WebShake`(+`WebShakeMixin`), `WebSymbioteDread` |
| Blob and behemoth rendering | `WebBlobRenderer`, `WebBehemothClient` |

### 7. Synced attachments and packets

| Attachment | Holder | Persist / copyOnDeath | Sync |
|---|---|---|---|
| `web_line`, `web_line_off`, `web_wings`, `web_charging`, `web_tether`, `web_pose` | WebPower | no | all |
| `web_held`, `web_hitch` | WebPower (set on *targets*) | no | all |
| `web_cooldowns` | WebPower | no | owner |
| `web_state` (State) | WebPower | no | server only |
| `web_symbiote` (WebSymbioteState) | WebSymbiote | **yes / yes** | all |
| `web_symbiote_cd` | WebSymbiote | no | owner |
| `web_symbiote_sulk` | WebSymbiote | yes / no | owner |
| `web_symbiote_strike`, `_wall`, `_staring`, `_tendrils_out`, `_ripping`, `_eating` | WebSymbiote | no | all |
| `web_symbiote_live` (Live) | WebSymbiote | no | server only |
| `web_tendril_grips`, `_combo`, `_climb`, `_tug` | WebTendrils | no | all |
| `web_swung` | WebAscension | yes / yes | **none** |
| `web_strike` | WebAscension | no | owner |
| `web_ascension_live` | WebAscension | no | server only |
| `web_behemoth`, `web_behemoth_strike` | WebBehemoth | yes / - ; no | all |
| `web_weblines` | WebBridges (level) | — | all |

- C→S: `web_swing`, `web_wings`, `web_charge_hold`, `web_symbiote_use`, `web_symbiote_struggle`,
  `web_symbiote_climb_latch`, `web_tendril_grab`, `web_tendril_smash`, combo `Attack`, `web_cut`, `web_trick`,
  `web_strain`.
- S→C: `web_swing_reject`, `web_pull`, `web_takeover`, `web_takeover_path`, `web_nudge`, `web_shake`,
  `web_symbiote_dread`, `web_sense`, and Wind's `ScreenFx` (vignette).
- Registration: `WebNet.register` (P/WebNet.java:212) plus each feature's own `register()`.

### 8. Doc mismatches

- **web.md Controls table:**
  - It lists G = Zip, V = 1-damage shot (Slowness IV), and B = 6 s Yank with "no fall damage".
  - Now: G = off-hand swing, Z = Zip, V = pellet or charged blast, B = starter webline then Yank, and fall damage
    applies.
  - `ATTACH_SLACK` is 0.94 in the docs and 1.0 in code; the "+3 safe fall" passive is gone.
  - "Testing" step 4 (G at a wall) is stale.
- **web.md Spider-Sense:** says one dodge per 400 t; the code has 5 charges.
- **web.md, the 0.8.9 rework section:** says "None of this has been run in game yet." The Risky list (mixins, Wall
  Crawl corner cases, takeover jerkiness) still applies.
- **MODLOG 0.4.6:**
  - "Instinct 45% raises Living Armor": the code never raises it.
  - "R/G are tendrils with the suit on": the code needs Living Armor; the suit only swings.
- **MODLOG 0.4.4:** "rampage +12%/min"; the code is 20%/min (`ARMOR_TAKEOVER_PER_MINUTE` 0.2). "Bite heals and
  feeds": the code only damages, by design ("never feeds on anything living").
- **Abilities.md:**
  - Consume "eat something small (5 hearts or less)": it kills, then grabs, carries and eats the corpse, and needs
    Kitchen.
  - Swing "up to 5 lines when mastered": that is hitches.
  - Swing "no fall damage while swinging": false (only the suit or armor waive it).
- **`WebSymbioteState` javadoc** says "kept through death": the record is copied, but `withdraw` unbonds on every
  death.
- **`sulk` javadoc** says "per time it has rejected you": it uses the exposure count, which `bond()` resets to 0
  (`reject` also zeroes it; `withdraw` keeps it).
- Plan-0.8 Track 4 (screen-takeover messages, the feeding chain, strikes, thrown-player ragdoll, FPM tendrils) is
  intent; parts are in code (`whisper`→`Dread`, `FirstPersonCompat`, feeding stages). Check each item against the
  code before assuming it is done.
- **Reported web bugs:** neither MODLOG.md nor docs/web.md has an open bug list for Web. web.md "Known gaps" only
  notes that feeding with a full hunger bar is impossible (vanilla won't start eating). There are no
  TODO/FIXME/XXX comments in either web package.

### 9. Bug-hunting notes

The likely bug spots are ordered roughly by how likely they are to bite.

1. **Stale `web_held` on the tethered target.**
   - `releaseTether` is the only thing that clears it. It is *not* called by the death branch of `WebPower.tick`
     (:450-460, which only removes STATE/LINE/TETHER), by `onLost` (:312), or by the DISCONNECT handler (:251, which
     only releases tendrils).
   - The target then stays ragdolled on every client.
   - If the target is a player, `core/Carry.java:309` refuses to let them carry while `HELD` is set.
2. **Stale hitch state on entities.**
   - `WebHitches.tick` (:157) drops a hitch whose entity is null, dead or in another dimension with `it.remove()`
     and never calls `release()`.
   - A hitched entity that changes dimension keeps `web_hitch` (lines drawn forever) and possibly `web_hitched`
     (−40% or −85% speed).
   - In the player cut/strain branch (:189) the method `return`s out of the whole server tick, skipping the other
     hitches that tick.
3. **Weightless leftovers from tendrils.**
   - `letGo` (P/WebTendrils.java:646) looks the target up in the *holder's* level. If the holder or target changed
     dimension, `weightless(target, false)` never runs.
   - For non-living holds (items, corpses, `BlockCluster`), `setNoGravity(true)` is **saved to NBT**, so a crash or
     unload mid-hold leaves floating corpses or items for good.
4. **No respawn or dimension-change reset anywhere.**
   - The client `onActiveChanged` (C/WebPowerClient.java ~115) fires only when the power id changes, or when
     `minecraft.player` is null (disconnect).
   - After a portal, the singleton `WebSwing` strands stay attached to old-dimension coordinates. The server keeps
     `state.swinging` until the 10-tick `nearSolid` check happens to fail in the new level.
   - There is no `ServerPlayerEvents.AFTER_RESPAWN`/`CHANGE_WORLD` handler in either package.
5. **Client and server takeover can disagree.**
   - The client ends its takeover by itself (`tickTakeover` C/WebSymbioteClient.java:587) when
     `level().getEntity(target)` is null (out of tracking range, or a flee target swap not yet received), or when its
     own countdown runs out.
   - The server is still `driving`: input unlocks on the client, but the server ignores `Grab`/`Smash`/`ClimbLatch`
     and keeps sending paths.
   - Server timeouts (`TAKEOVER_LIMIT`, `RUN_TICKS`) and client countdowns are independent.
6. **Lashes that never land.**
   - `tickStrikes` only runs while ARMOR or BULK is set (`tick` ~:930), and the else-branch deletes `STRIKE`.
   - So `desperate()` (:1522) lashes out of armor do no damage.
   - Consume's finishing lash and the panic lash apply damage separately, so they work; the visual is cut.
   - `lash` overwrites any strike in progress.
7. **`retaliate` guard** (:2253): `player.isCreative() && !player.isAlive()` looks like a typo for `||`.
   As written, creative players and dead players are not excluded.
8. **The feeding loop can repeat.**
   - When a kill's corpse never appears (died in lava, Kitchen didn't spawn one), `abortFeeding` leaves
     `live.craving = true`. `selfFeed` then re-hunts every 20 t; it is gated only by `feedStage`, not by
     `takeoverReadyAt`.
   - Without Kitchen, `huntable` is false, so it can't loop, but it can never eat either.
9. **Symbiote state not reset on `onLost` or `reject`.** These leave the synced `WALL` (and its crack progress),
   `STRIKE`, `EATING`, `STARING` and `TENDRILS_OUT` in place. `onLost` also leaves `SULK`, and `WebPower.onLost`
   leaves `CHARGING`.
   - After `reject`, `WebSymbiote.tick` returns early (not bonded), so `tickWall`/`tickEating` never clean up.
   - Check how the renderers handle a stale `WALL`/`STARING`.
10. **Relog or restart mid-takeover.**
    - `Live` (engulfed, feedingArmor, shroudUntil, armorOnAt) is lost, but the SUIT/ARMOR/BULK flags persist.
    - BULK is fixed on the next tick. SUIT may stay forced on, and ARMOR comes back on its own.
    - The Shroud's Invisibility effect survives without `shroudUntil`, so attacking no longer ends it.
    - `endShroud` removes *any* Invisibility, potions included.
11. **Unvalidated client claims.**
    - The `Swing` packet isn't mastery-gated.
    - `WebStrain` doesn't check that the sender is actually in a two-web slingshot: any Living Armor player can send
      progress 1 and break a 3×3 behind them.
    - `ChargeHold` grants fall immunity while "held" with the suit on.
    - `Nudge` is applied unconditionally on the client.
12. **Dead or misleading constants.**
    - `WINGS_COOLDOWN` is unused; the server never auto-folds wings on its own (`WINGS_TICKS`).
    - The HUD B-chip shade uses `YANK_COOLDOWN` even for the webline's 160 t or the mastered 0.
    - The javadoc for WINGS sits above `CHARGING` (P/WebPower.java:111).
13. **Fall-damage policy.** The house rule is that fall damage always applies outside creative. But the suit or
    Living Armor cancels all fall damage (P/WebSymbiote.java:638), and `chargeHold` and wall/instinct catches reset
    fall distance. That is intentional per 0.8.19, but it is a common "why no fall damage" report.
14. **Spider-Sense dodge** applies to any attacker (friendly fire, other players' tendril lashes). Its charges live
    in `Live` and reset on relog.
15. **Long methods worth splitting before a deep fix:** `WebSymbiote.tick` (~310 lines; each phase mutates `next`,
    and an early `return` after a fire exposure skips the rest), `WebTendrils.tick`, `WebSwing.tickSwing` (~140),
    `WebPower.tick` (~90).

**Coupling points when editing.**
- Every class declaring an `AttachmentRegistry.create` must stay in `JaymosPowers.registerAttachments()`. For Web
  these are `WebAscension, WebBehemoth, WebBridges, WebPower, WebSymbiote, WebTendrils`. A new attachment in another
  web class (for example `WebHitches`) must be added there too, or dedicated-server clients won't receive it.
- Lang lives in `src/main/resources/assets/jaymospowers/lang/parts/web.json` (144 keys, merged by
  `processResources`). Whispers are `message.jaymospowers.web_symbiote.<key>`; narration is `...say.<act>.1-3`
  (`SAY_LINES` 3); panic is `panic.1-8` (`PANIC_LINES`); alarms are `alarm_fire|alarm_noise.1-4`; unlocks are
  `unlock_<ability>`. Key guide keys are `power.jaymospowers.web.key.<slot>[.symbiote]`.
  - All whisper keys used in code currently exist. Changing `*_LINES` counts requires matching lang entries.
- **Mixins** (all `require 1`):
  - Server, `jaymospowers.web.mixins.json`: `WebFoodPropertiesMixin` (`FoodProperties.onConsume`),
    `WebHostZombieMixin` (`Zombie.isSunSensitive`/`convertsInWater`), `WebServerLevelMixin` (`ServerLevel.gameEvent`).
  - Client, `jaymospowers.web.client.mixins.json`: Avatar/Entity/LivingEntity renderer `extractRenderState`,
    `ModelFeatureRenderer.prepareModel`, `FirstPersonHandsAndItemsRenderer.submitHandsWithItems`,
    `LivingEntity.jumpFromGround`, `GameRenderer.bobHurt`, `LocalPlayer.shouldStopRunSprinting`,
    `KeyboardInput.tick`.
- **Core hooks:**
  - `core/Powers.key/mode/ultimate` dispatch keys.
  - `JaymosPowersClient` routes every power key to `WebSymbioteClient.strugglePowerKey` while seized.
  - `core/Flames` → `WebSymbiote.nearFlames`.
  - `core/Carry` reads `WebPower.HELD/HITCH`.
  - `core/BlockCluster`/`Debris` are used by chunks and rips.
  - `GravityPower.isCorpse` defines "corpse".
  - The X key is shared with Wind's Tempest Kick.
- **Optional mods:** only through `KitchenCompat` (`LOADED = isModLoaded("jaymoskitchen")`, which reaches Kitchen by
  reflection for `carry`/`butcherRough`) and `FirstPersonCompat`. Never call Kitchen classes directly.
- **Shared static state** (`HOLDS`, `TUGS`, `CROSSES`, `THROWN`, `HITCHES`, `WebStrain.CRACKING`) is cleared on
  `SERVER_STOPPING`. It is only partly cleared on disconnect: `WebTendrils.forget` runs, but `CRACKING` and
  `STRAIN` are not cleared.

---

## Jaymo's Kingdoms

**What it is:** `kingdoms/` (mod id `jaymoskingdoms`, **v0.5.0**, MIT). It is a standalone Fabric 26.3 mod that ships in the pack as an
override jar. It is original code and art: the ideas come from *Tale of Kingdoms*, but nothing is copied from it, because its code
is GPL-3.0 and its art is all rights reserved. Kingdoms grow out of the world's villages. Each one has a king who is a boss
fight, plus guards, archers and builders. It also has a treasury, real storehouses, ranks, and reputation. Around the
kingdoms there are barbarian and illager camps that raid, Grim-ruin fiefdoms, and wars between realms.

Sources: `kingdoms/src/main/java/com/jaymoskingdoms/` (about 60 classes, about 17k lines), `kingdoms/MODLOG.md`,
`docs/Kingdoms-Plan.md` (the original plan and the user's answers) and `docs/Kingdoms-Plan-0.3.md` (factions, storehouses,
builders, theft, barbarians, war). Most of 0.3-0.5 is marked "built, not yet seen in game". The 0.3.0 playtest covered
founding, production, builders and a war party. Theft, plots and villages had not been tried at that point.

Hard dependency: Fabric API only. Everything else is optional and listed under `suggests`: Better Combat, Epic Knights
(`magistuarmory`), ATi Structures and MCA. Grim Kingdoms, Slavic Armory and Moog's Paths are detected or data-driven.

### Factions (the model)
- **`Faction`** is an immutable record with these fields: id, type, name, colour, seat, leader UUID, members, treasury, claims,
  overlord, fallen, relations, holdings. **`Factions`** stores and queries them on the Overworld. **`FactionType`** has four types:
  - `KINGDOM`: led by a king (boss).
  - `FIEFDOM`: a vassal realm led by a lord (boss). It wears its overlord's colours and sends 30% of its taxes to the overlord.
  - `VILLAGE`: led by an elder, who is an ordinary villager and not a boss. Its iron golems act as its guards.
  - `BARBARIAN`: a camp led by a chief or warlord. Illager camps use this type too, with `kind = "illager"`.
- **Relations** (`Relation`, `Relations`) can be allied, neutral, rival or war. A vassal counts as allied with its overlord. Camps are
  always at war with every settlement, and barbarian and illager camps are at war with each other. When folk are attacked,
  they only alert their own side.

### Founding and seats (`KingdomEvents`, `Seats`, `Storehouse`, `Holds`, `Castle`)
- **Only inside generated villages:** a bell founds something only if it sits within 2 blocks of a village piece (`#minecraft:village`).
  In this pack that means **Epic Structures: Villages** towns. A player has to come within **48** blocks of the bell, and the
  village must be loaded within 112 blocks.
  - With no realm seat within **320** blocks, the bell becomes a **kingdom**.
  - If a realm seat is nearer than 320, it becomes a **village** (5x5 chunks, an elder and a shed).
  - Within 64 blocks of a seat, the bell just counts as part of that town.
- **The seat is the village's church** (a piece path containing `temple`), or failing that its largest building:
  - A throne (a stair) goes at the far end of the main floor, with the kingdom's wall banner above it.
  - A 3x3x3 **panic room** is dug under the floor beside the throne. It has a trapdoor, a ladder, the treasury chest and a
    lantern.
  - 2 guards stand in the hall and 3 at the door. Up to 3 archer posts go on nearby rooftops.
  - Only air is ever replaced, so the villagers keep their beds and job blocks.
- **The storehouse** (`Storehouse.adopt`) goes into another building that nobody sleeps in, such as a library, smithy or stable. It
  gets up to 10 chests and barrels plus a depot by the door. If no building works, it falls back to a timber hall on open
  ground. Both buildings are **held** (`Holds`), which means builders never tear them down.
- **Land:** a kingdom claims 13x13 chunks (`CLAIM_RADIUS` 6), a village 5x5, a camp 3x3, a Grim fiefdom 4 chunks out and a Grim
  camp 2 chunks out.
- **Starting folk:** 8 guards, archers on the posts, and a builder crew of 3 that grows to 6.
- **Legacy castles** (`Castle`: a keep, a 31x33 castle with a curtain wall, or a 4-storey tower) can still be built with
  `/kingdom found keep|castle|tower`.

### Folk, roles and looks (`Folk`, `Outfits`, `Climate`, `Banners`-adjacent heraldry, `KnightsCompat`)
| Role | Stats / behaviour |
|---|---|
| Guard | Iron or mail, sword (30% carry a spear). Stays within 24 blocks of home. Fights monsters, hostile players and its side's attackers |
| Archer | Bow, stays on its post, shoots out to 28 blocks |
| **King** (boss) | **300 HP, 9 dmg**, crown, netherite trimmed in gold, glinting sword and shield, boss bar. At **40%** HP he rallies: Strength, Resistance, Speed, a horn, and his guards called in. A third of the time he hides in the panic room |
| **Lord** (fiefdom boss) | **220 HP, 7.5 dmg**, trimmed diamond, purple boss bar, the same rally |
| Foreman / Worker | 20 HP, don't fight, flee when hurt, carry their loads visibly |
| Barbarian / **Chief** | Raider: 30 HP, axes and hides. Chief: **160 HP, 8 dmg**, diamond axe, red boss bar, rally at 40% |
| Illagers / **Warlord** | Vanilla pillagers, vindicators, evokers and a ravager. Warlord: a vindicator with **200 HP**, a diamond axe and the ominous banner, rally at 40% |

- **Heraldry** (`Outfits`, 0.5.0) is derived from the realm id:
  - a field colour, a contrasting tincture and one of 18 charges;
  - a trim pattern whose material matches the colour;
  - one of three harness styles (plate, mail under a tabard, or half-plate).
  - A fiefdom bears its overlord's arms inside a border.
- **Climate** (`Climate`) adds regional touches: desert turbans and sand trousers, snow hoods, taiga furs, jungle spears and so on.
  Generated skins differ by land (temperate, sunny, cold) and by face. They are drawn by `tools/gen_folk.py`, `gen_barbarians.py`
  and similar scripts.
- **Epic Knights compat** (`KnightsCompat`): items are looked up by registry id, with no compile dependency. Each role has a
  loadout: bascinet and platemail with the arms for guards, gothic plate and a claymore for kings, rusted gear for barbarians,
  and so on. Desert realms switch to **Slavic Armory** pieces when that add-on is present. Neither mod has a 26.3 build yet, so this
  is all dormant for now.

### Reputation and ranks (`Reputation`, `Rank`, `McaCompat`)
- **Reputation:** each player has a reputation with each faction, from -1000 to 1000. Guards attack at **-100 or below** (hostile).
  You need **0 or more** to pledge. Labels: hated ≤-500, neutral <100, friendly <400, honoured <800, revered.
- **Gains and losses:**

  | Event | Change |
  |---|---|
  | Kill a guard | -40 |
  | Kill a builder | -30 |
  | Kill a village elder | -150 |
  | Kill a lord | -250 |
  | Kill a king | **-400** with his realm, **+60** with every other kingdom |
  | Kill monsters on a realm's land | +2 (+3 standing for members) |
  | Donate to storage | +1 per 8 items, capped at 25 a day |
  | Clear a camp | **+100** with every settlement (+40 for players within 48 who helped) |
  | Kill a raider during a raid | +5 |

- **Standing → rank** is tracked per member of a realm: Peasant 0, **Soldier 100**, **Knight 300**, **Lord 700**.
  - Knights and above may take items from their faction's storage.
  - Lords may withdraw from the treasury, mark plots, declare war and offer peace.
- **Void Crown** (from Jaymo's Powers, looked up by id): wearing it pins your reputation with every faction at -1000. The earned value is
  hidden, not wiped.
- **Audience:** right-clicking any folk prints a chat line saying what that faction thinks of you. A ruler also sends a clickable
  pledge offer. The **talk screen** from `docs/HANDOFF.md` (talking to folk, asking about the kingdom, rumours) is **not
  implemented**: there is no screen or packet for it in the code.

### Treasury, production and economy (`Production`, `Economy`, `Storage`, `Materials`)
- **Treasury:** emeralds. Use `/kingdom deposit <n>` and `/kingdom withdraw <n>` (lords only), with n between 1 and 2304.
  - **Daily taxes:** 2, plus 1 per 2 villagers, plus 1 per member.
  - Fiefdoms send 30% of their taxes to their overlord.
- **Production** runs once per in-game day, using real items in the storehouse:
  - **Labour** = 4 + 2 per villager + 1 per folk.
  - **Materials** = 2 per labour, up to 192: logs in proportion to the land's forest share, and the rest cobblestone and stone.
  - **Food** = 2 + 1 per villager + 1 per 4 crops, up to 128.
  - Each type gets a share: kingdom 100%, fiefdom 80%, village 60%, camp 30%. Up to 3 missed days are caught up.
- **Economy health** is shown in `/kingdom info`:
  - *Healthy*: the next building's materials and 32 food are in storage.
  - *Personnel*: kingdom 12, fiefdom 10, village 2, camp 4, plus 1 per 6 villagers. Every quarter day, one vacancy is filled by
    drafting a villager, but the village always keeps at least 3.
  - *Peace*: no war, and no attack in the last 30 s.
- **Building costs** are priced in logs and cobble, counted in eighths of an item (`Materials`). A village house costs about 50 logs
  and 25 cobble. A realm house costs about 30 logs and 90 cobble.

### Construction and planner (`Planner`, `Construction`, `Buildings`, `Blueprint`, `Works`, `Plots`, `Builders`, `Ruins`, `Debris`)
- **When it builds:** while the economy is healthy, staffed and at peace, the foreman picks the next entry in the growth order
  and starts it. Only one project runs at a time.
- **Village growth order:** houses, farm, well, market stall and crossroads, then more houses, and a palisade at a size of 22.
- **Realm growth order:** houses, farm, smithy, roads and well first; then the mason's yard at 20, market square at 24,
  stable at 28, barracks at 32, and walls, towers and gatehouses at 40.
- **Siting:** sites are searched ring by ring out from the seat, on open natural ground inside the faction's land, preferring
  spots near roads.
- **Real-time work:**
  1. The foreman stakes the site with fences and a sign.
  2. Workers haul up to 16 items at a time from the real containers.
  3. They level the ground, then lay blocks bottom-up at about 1.2-1.8 s per block.
  - Work pauses when materials run out or the faction is under attack or at war.
  - A worker who gets stuck is teleported to the site in a puff.
  - Progress is saved, so half-built projects resume after a restart.
- **Never touches player builds** outside plots.
- **Plots** let a lord direct the builders:
  - `/kingdom plot mark [w d]` marks a plot (5-32 blocks a side).
  - A Plot Stake item marks an 11x11 plot. Recipe: 2 sticks and 1 wool, makes 2.
  - On a plot the builders clear anything, even the lord's own builds, and put it into storage.
- **Old structures** (0.4.1): builders may tear down abandoned village houses and generated ruins, and their loot goes into
  storage. They never touch occupied beds or job blocks, bells, village streets, or anything near a seat.
- **Debris** (Grim holds): builders clear cobwebs, vines and rubble heaps, and patch one-block holes in walls, before any
  growth starts.

### Theft and crime (`Theft`)
- **Where it applies:** inside a faction's land, or within 48 blocks of its seat.
- **Severity:**
  - *Light* (-10): rummaging in storage, breaking crops, killing livestock.
  - *Medium* (-35): taking from storage, breaking beds or job blocks.
  - *Heavy* (-90): breaking storage or the bell, killing villagers or golems, or taking 32 or more items at once.
- **Witnesses:** folk, villagers or golems with line of sight within 16 blocks (8 if you sneak). The nearest witness shouts
  "Thief!".
- **Consequences:**
  - Medium crimes set guards and golems on you for **2 minutes**.
  - Heavy crimes make you an **outlaw for 2 days**, hunted within 32 blocks.
  - Unseen theft is noticed about 5 minutes later and costs -3.
- **Exemptions:** knights and above (for storage crimes) and creative players. Camps have no law. Plunder during a war isn't
  theft.

### Barbarian camps, illager camps and raids (`Camps`, `CampBuildings`, `Barbarians`, `IllagerCamps`, `IllagerBuildings`, `Illagers`, `Raids`)
- **Barbarian camps:**
  - **Where:** 150-300 blocks from a realm, at most 2 within 360 of it, 160 apart from each other. Every minute there is a 20%
    roll per realm.
  - **Layout:** a jagged log palisade with skull gateposts, a campfire, the chief's tent with a hoard chest, hide tents, and a
    loot-pile storehouse.
  - **Growth:** one raider every 2 days, up to 12. Builders add a totem, tents, watch posts, palisade sections and pens.
- **Illager camps** (0.4.0):
  - **Where:** only in pillager biomes, with a 15% roll per minute per kingdom.
  - **People:** real vanilla illagers that never join vanilla raids. An evoker joins at 3 days, a ravager at 5, and a second
    evoker at 7.
  - **Layout:** dark-oak fence ring, the warlord's pavilion, a watchtower with a sentry, and a caged **allay** you can free
    once the camp is cleared.
  - **Growth:** the camp grows without builders, one building per day.
- **Raids (war parties from camps):**
  - Every 10 s, a camp has a 12% chance to raid the nearest settlement within 420 blocks, if a player is within 80 of that
    settlement. Cooldown: 2 days per camp and 1 day per target.
  - **Party size:** 3 + 1 per 2 raiders in the camp, up to 8. It is announced with horns.
  - The party marches on the seat, then each raider takes up to 32 of one item from storage and carries it home to the loot
    pile.
- **Clearing:** killing the chief or warlord gives +100 reputation with every settlement. The camp scatters and its tents rot
  away. The palisade remains as a ruin.

### Wars between kingdoms (`Wars`, `WarData`, `WarParties`, `Banners`, `WarCommands`)
- **Declaring:**
  - A lord uses `/kingdom war declare <name>`.
  - NPC kingdoms can also start wars. Neighbours within 800 blocks may become rivals (15% per day). A kingdom then has a 30%
    chance per day to declare on its weakest rival within 1000, if its army is at least 1.25x the rival's.
- **Timing:** a **1-day warm-up**, a **5-day maximum**, and a **3-day truce** after peace. Fiefdoms fight alongside their
  overlord.
- **During a war:**
  - Construction stops on both sides.
  - Folk attack enemy folk and enemy sworn players on sight.
  - **War parties** are sent with a 15% chance, at most every quarter day. Each has 3-9 soldiers who plant a frontier banner and
    besiege the seat for 3 minutes.
  - Wars with no players nearby are resolved off-screen.
- **Banners:**
  - A sworn player plants their realm's banner on an enemy chunk that borders their own land or the enemy's frontier.
  - Holding it **60 s** with no enemies within 16 claims the chunk.
  - Holding a banner **4 minutes** within 24 of the seat makes the seat fall.
  - Enemy folk come to tear banners down.
- **Score:**

  | Action | Points |
  |---|---|
  | Enemy folk killed | 2 |
  | Enemy leader killed | 40 |
  | Chunk claimed | 10 |
  | Items plundered | 1 per 16 |

  A side wins outright at **120 points with a 50-point lead**.
- **Peace terms**, by the winner's lead:

  | Lead | Terms |
  |---|---|
  | Under 15 | White peace |
  | 15+ | Tribute: 30% of the treasury plus 6 loads of goods |
  | 45+ | Tribute plus 4 chunks ceded |
  | 90+ | **Vassalage**: the loser becomes a fiefdom |

  Killing the enemy ruler, or holding their seat, ends the war at once with vassalage. Lords can also negotiate:
  `/kingdom war peace offer|accept`.
- **Reputation:**
  - +6 per enemy folk killed (+50 for a leader).
  - +15 for taking a chunk.
  - +8 for tearing down an enemy banner.
  - Allies of each side shift by ±2.

### A fallen king
When a king dies outside a war, one of three outcomes is picked at random:
- a successor takes the throne after 1 day;
- the realm becomes the killer's realm's **fiefdom**, led by a lord;
- the realm falls to **ruin**: the guards scatter and the land is lost.

### Grim Kingdoms fiefdoms and sites (`GrimSites`, 0.5.0; only with Grim Kingdoms loaded)
- **Scan:** every 30 s the mod checks Grim structures within 5 chunks of players. A structure qualifies if it is within 600 blocks
  of a kingdom, unclaimed, 96 blocks from any settlement, and nobody is within 40 blocks.
- **Big holds** (fortresses, citadels, keeps, Zweiberg, the colosseum and so on) become a **fiefdom** of the nearest kingdom:
  a throne in the largest roofed building, a lord, 5 guards, archers, a storehouse and a crew.
- **Small or wild structures** (camps, outposts, watches) become **barbarian camps**.
- **Limits:** at most 2 fiefdoms and 2 camps per kingdom.
- **Expansion:** after the rubble is cleared, builders raise **one more Grim building** block by block from its template after 3
  days. The cap is 26 blocks across and 5000 blocks total.
- With Grim loaded, wild barbarian camps prefer a free Grim site when one exists.

### Other compat
- **ATi compounds** (`AtiCompounds`, `CompoundStructure`, 0.4.1; only with ATi Structures): ATi's large and medium buildings
  generate together as rings of 3-4 around an open middle, about one compound every 1000 blocks. The single structures are
  removed from placement by a mixin.
- **MCA** (`McaCompat`, `McaBridge`, `mixin/mca`, 0.4.2):
  - **Hearts:** 0.2 x your MCA village hearts is added to your faction reputation, clamped to -200..+300.
  - **Ranks:** Soldier counts as merchant, Knight as noble and Lord as mayor in MCA. Being hostile to a faction makes you an
    outlaw in its village.
  - **Drafting:** settlements draft MCA villagers into service, spouses, kids and friends included.
  - **Elders:** an MCA elder keeps their own name.
- **Better Combat mob combat** (`MobCombat`, `BetterCombatBridge`, client `MobAnimations`, `PalMobAnimator`, 0.4.0):
  - **Who:** guards, kings, armed zombies, vindicators and wither skeletons.
  - **Moves:** they swing Better Combat combos, with a visible wind-up, arc hits on up to 5 targets, and animations through
    Player Animation Library.
  - **Sidestep:** folk can dodge a player's blow: guards 15%, archers and barbarians 10%, leaders 25%.
- **Moog's Paths** (data in `data/jaymoskingdoms/moogs_paths/`, 0.5.0): adds extra roads leading out of villages, Grim
  castles and ATi compounds, styled by biome. They only appear in new chunks.

### Commands (`KingdomCommands`, `WarCommands`, `BuildCommands`, `Camps`; `/faction` is an alias)
- **Players:**
  - `/kingdom list`, `info [name]`, `pledge <name>`, `leave`, `deposit <n>`, `withdraw <n>` (lords), `reputation`.
  - `/kingdom project` (what the builders are doing), `plot mark [w d]`, `plot build <building>`, `plot list`, `plot clear`.
  - `/kingdom war status`, `war declare <name>`, `war peace offer|accept [name]`.
- **Ops:**
  - `found [keep|castle|tower|village]`, `storage`, `produce`, `storehouse`, `crew`, `hire`.
  - `relation <a> <b> allied|neutral|rival|war`.
  - `project start|finish|cancel`.
  - `war start <a> <b>`, `war end <name>`, `war party`, `war npc`.
  - `camp found [barbarian|illager]`, `camp raid`, `camp grow`, `camp info`.

## Jaymo's Kitchen

`kitchen/` (mod id `jaymoskitchen`, **v0.4.23**, matching the jar in `libs/`). It is MIT, except for the
third-party parts listed below, which keep their own licences. It isn't on CurseForge, so it ships as an override jar. Jaymo's Powers
**requires** it, and since 0.8 it is touched only through `KitchenCompat`.

**What it adds:**
- **Cooking minigames:** pick a dish and press the steps in order, including Stir, Flip and Bake. The result is Perfect (with a buff),
  Good or Burnt.
  - **Where:** campfires and smokers, and Cooking for Blockheads ovens. A pot or pan in hand picks the station.
  - **Ingredients:** drawn from the whole connected Cooking for Blockheads kitchen.
  - **Kit:** cookware that stays in the crafting grid (pot, pan, saucepan, tray, rolling pin) and a range of dishes and foods.
- **Corpses and butchering:**
  - Mobs leave corpses that can be carried, thrown, bagged, burned or put into carts.
  - Butcher blocks come in every wood, with a 2x2 block for cows. Hostile corpses don't rot, and zombies get back up.
  - Zombie players rise from dead players.
  - Jaymo's Powers uses these corpses (the symbiote feeds on them).
- **Other gear and mechanics:**
  - A wearable quiver.
  - Leg shots that knock creatures down.
  - Sowing, and animals that eat dropped seeds.
  - Herb "joints".
  - Crafting tables that keep their contents.
- **Horses:**
  - Saddlebags.
  - A wagon.
  - Supply carts, animal carts and plows in 12 woods (a port of **NiftyCarts**, MIT).
  - Horse-powered mill machines (from **Horse Power**, LGPL-3.0).

**Dependencies:** Fabric API, **Cooking for Blockheads** (26.3.0.2 or newer) and **Balm**. Both are required and built against, but not
bundled, because their licences allow use in modpacks but not re-uploading.

## Third-party mods in the pack

**How the pack is put together:**
- **Sources:** `build.gradle` holds `packMods`, which are in the CurseForge import manifest with a project id and file id each. It
  also holds `packModsNoCf`, which only ship in `modpack/mods`, the server and the dev game because their CurseForge ids are
  still missing. The pack jars are local: `kitchenJar`, `mcaJar` and `kingdomsJar`.
- **Outputs:** `Build Modpack.bat` runs `gradlew build packageModpack`, which produces:
  - `modpack/Jaymos-Powers-Modpack-<v>.zip`: the CurseForge import zip, with Powers, Kitchen, MCA and Kingdoms as overrides;
  - `modpack/mods/`: every jar;
  - a sync of the friends' server mods folder, skipped while that server is running;
  - an update of the user's CurseForge profile;
  - `Deck-mods-<v>.tar.gz` for the server, which now runs on a Steam Deck (`deck/README.md`, `setup.sh`, `start.sh`; Java 25 and a
    playit.gg tunnel; up to 6 GB RAM).
- **Server vs client:** there is no separate server or client mod list. The server and the Deck get every jar in `modpack/mods`, and
  clients get the CurseForge zip. "Side" below is where a mod actually does its work.

| Mod | What it does | Side | Notes |
|---|---|---|---|
| Jaymo's Powers | The pack's main mod: superpowers | both | Root project; override jar |
| Jaymo's Kingdoms 0.5.0 | Kingdoms, factions, war (above) | both | Self-built (`buildKingdoms`), override jar |
| Jaymo's Kitchen 0.4.23 | Cooking, corpses, carts, mills (above) | both | Self-built, `libs/`, override jar; required by Powers |
| Fabric API | Core Fabric hooks and library | both | CF 306612 |
| Balm | BlayTheNinth's multi-loader library | both | Needed by Cooking for Blockheads |
| Cooking for Blockheads | Multiblock kitchens, ovens, fridges, recipe book | both | Required by Kitchen |
| Pantry for Blockheads | Pantry and storage add-on for Cooking for Blockheads | both | From the friends' server list |
| Balkon's WeaponMod: Legacy | Spears, halberds, muskets, crossbows and other weapons | both | Used by Powers' Pocket Arsenal through the registry (compat) |
| Architectury API | Multi-loader library | both | Library |
| Cloth Config | Config screens and library | both | Library (26.3.158) |
| Better Combat | Animated weapon swings and combos | both | Kingdoms' `MobCombat` hooks into it |
| Player Animation Library | Player animation engine | both | Needed by Better Combat; Kingdoms uses it for mob swings |
| Combat Roll | Dodge-roll key | both | Powers has `CombatRollTaps` compat |
| Nature's Compass | Compass that finds biomes | both | |
| JEI | Recipe and item viewer | client (works on both) | |
| MezzConfig | Config library for JEI (mezz) | both | Library |
| Xaero's Minimap | Minimap and waypoints | client | |
| Sodium | Rendering performance | client | |
| Ok Zoomer | Zoom key | client | |
| Weapon Master (YDM) | Shows hotbar weapons on the body | client (mostly) | |
| LambDynamicLights | Held torches and other items light the area | client | |
| Sound Physics Remastered | Reverb, echo and muffling | client | |
| Lootr | Loot chests give each player their own loot | both | |
| AmbientSounds 6 | Biome, cave, water and weather ambience | client | Needs CreativeCore; stands in for Dynamic Surroundings (no 26.3 build). Pack 0.10.49 |
| CreativeCore | CreativeMD's library | both | Library |
| Sounds (imb11) | 170+ new sound effects for screens, items, blocks | client | Needs MRU + YACL. `packModsNoCf`: **awaiting CF id**, not in the CF zip |
| MRU, YetAnotherConfigLib | Libraries for Sounds | both | Library |
| Subtle Effects | Sparks, snoring, dust clouds, burning, heartbeat and more | both (every client needs it) | Needs Fzzy Config + Fabric Language Kotlin |
| A Good Place | Blocks animate into place when placed | client | |
| Grindstone Sharper Tools | Sharpen tools on a grindstone for extra hits | both | Needs Collective |
| Collective | Serilum's shared library | both | Library |
| Moog's Voyager Structures | Many new worldgen structures | server (worldgen) | Needs Moog's Structure Lib |
| Moog's Structure Lib | Structure library | server | Library |
| Moog's Paths | Biome-styled roads, trails and bridges | server (worldgen) | Kingdoms adds its own road data |
| ATi Structures, Vanilla Edition | Medieval castles, forts and ruins from vanilla blocks | server (worldgen) | Kingdoms groups them into "compounds" |
| Epic Structures: Villages | Large sprawling villages | server (worldgen) | **Where kingdoms are founded** |
| Epic Structures: Dungeons | Plains, desert and snowy dungeons | server (worldgen) | Nothing in Kingdoms keys off it |
| Grim Kingdoms, Vanilla Edition | Castles, citadels and ruins | server (worldgen) | `packModsNoCf`: **awaiting CF id**, not in the CF zip; Kingdoms' `GrimSites` |
| First-person Model | Your body visible in first person | client | `packModsNoCf`: **awaiting CF id**; Powers has `FirstPersonCompat` |
| Not Enough Animations | Third-person animation polish (needed by First-person Model) | client | `packModsNoCf`: **awaiting CF id** |
| Minecraft Comes Alive (MCA Reborn) | Human villagers: relationships, marriage, family, village ranks | both | **Self-built jar** from the authors' `dev/26.3` branch (GPL), shipped as an override until CF project 535291 has a 26.3 file; Kingdoms' `McaCompat` |

**Compat-only, detected at runtime:**
- **Shipped and wired:** MCA, ATi Structures, Grim Kingdoms, Better Combat, Player Animation Library, Moog's Paths (data only), WeaponMod
  (Powers, `suggests`), Combat Roll, First-person Model (Powers).
- **Not in the pack** (no 26.3 build; they turn on by themselves if added later):
  - **Epic Knights: Shields, Armor and Weapons** (`magistuarmory`): faction armour loadouts and heraldry via `KnightsCompat`.
  - **Epic Knights: Slavic Armory** (`slavicarmory`, needs Epic Knights): eastern harness for desert and badlands realms.

**Removed from the pack:**
- Distant Horizons (until 0.4.5).
- Towns and Towers and Cristel Lib (0.8.4): their villages clashed with Epic Villages.
- Traveler's Backpack and Forge Config API Port (0.8.14).

`profileMods` moves these out of the user's CurseForge profile.
