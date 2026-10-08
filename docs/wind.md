# Wind Control

Superhero flight with momentum, a sonic boom, and three wind buffs that you charge by holding a key. The flight's
feel is inspired by the "Viltrumite Flight" mod. That mod is All Rights Reserved, so none of its code or assets are
used here. All sounds and particles are vanilla.

## Controls

| Key (default) | What it does |
|---|---|
| **R** (Ability 1) | Toggle flight on or off. Taking off from the ground gives an upward burst. |
| **Double-tap Jump while sneaking** | Take off (from NONE to HOVER). |
| **WASD / Jump / Sneak** (HOVER) | Move slowly; Jump rises and Sneak sinks. Sinking onto the ground lands you. |
| **Sprint** (hold) | Go to CRUISE, flying fast where you look. Holding it accelerates and keeps your speed. Let go and you slow back down to HOVER. |
| **Sneak** (CRUISE/SONIC) | Lock the current cruise speed, so you don't have to hold Sprint. Sneak again to unlock. |
| **Back (S)** (CRUISE) | Brake. This also unlocks the speed. |
| **G** (Ability 2, hold) | Tailwind |
| **V** (Ability 3, hold) | Gale Barrier |
| **B** (Ability 4, hold) | Zephyr's Breath |

### Flight states
- **NONE**: on the ground or falling normally.
- **HOVER**: floating. Movement is close to creative-flight speed, with smooth acceleration and drag. When idle you
  bob gently.
- **CRUISE**: fast flight in the look direction. Your velocity swings round to your look direction gradually, so you
  drift through turns.
- **SONIC**: CRUISE at 80% or more of top speed. Entering it triggers the sonic boom:
  - a vapour ring ahead of you, a sonic-boom shockwave behind you, and a flash on the horizon
  - the warden sonic boom plus a low explosion, which other players hear too
  - a camera shake, then a light tremble for as long as you stay in SONIC

Walls and floors stop you. Only the blocked part of your velocity is removed, so you slide along surfaces. A hard
crash shakes the camera and makes a thud, but does **no damage** (by design, for now).

### Abilities
Hold the key to channel. Wind wisps spiral in around you, a chime rises in pitch, and the HUD shows a meter. Release
to cast. A longer channel, up to 2 s, gives a stronger and longer buff. At full charge the ability fires on its own.
Every ability starts its cooldown when it's cast.

| Ability | Effect (weakest to full channel) | Cooldown |
|---|---|---|
| **Tailwind** | Speed and Jump Boost for 5–20 s (level II from half charge), plus a forward dash of 0.7–1.5 blocks/tick. | 20 s |
| **Gale Barrier** | Resistance for 6–18 s (level II from 75% charge) and +0.5–1.0 knockback resistance. Projectiles that come within 4.5 blocks are blown back, and you become their owner. On release, hostile mobs within 6 blocks are pushed away. | 30 s |
| **Zephyr's Breath** | Slow Falling for 10–30 s and Regeneration for 5–12 s (level II at full charge), for you and every player within 6 blocks. | 45 s |

## HUD
The HUD stays clear of the core's top-left badge.
- **Under the crosshair while flying:** the state (HOVER / CRUISE / SONIC), your speed in m/s, LOCKED when the speed
  is locked, and a speed bar with a gold tick at the sonic threshold.
- **Over the crosshair while channelling:** the ability's name and its charge meter. The meter pulses when it's full.
- **Right of the hotbar:** chips for G, V and B, each showing the ability's icon, the bound key, a cooldown shade and
  the seconds left. On narrow screens the chips move above the hunger bar.

## How it works
- **The flight is client-side** (`WindFlightController`, run from `PowerClient.tick`):
  - It keeps its own velocity and steers it toward what the controls ask for, then calls `setDeltaMovement` every
    tick. Vanilla gravity and drag are thrown away, because the velocity is set fresh each tick.
  - Vanilla `abilities.flying` and mayfly are not used.
  - Collisions are read back from the player's post-move `horizontalCollision`, `verticalCollision` and
    `deltaMovement`.
- **`wind_flight` (C2S)** carries the state ordinal and the lock flag. It's sent when either changes, and re-sent
  every 2 s while flying. The server checks that the player has the power and that `WindFlight.canFly` holds;
  otherwise it answers with a stop.
- **On the server, while flying** (and after flying, until the player lands):
  - `allowsFloating` and `skipsMovementCheck` return true.
  - The fall distance is reset every tick.
  - `PlayerFallMixin` cancels `causeFallDamage`, which covers the landing tick itself.
- **What other players see:**
  - The state is put in a synced attachment, `jaymospowers:wind_flight_state`, which drives everyone's superman pose.
  - The server sends cloud trails, whooshes, the take-off gust and the sonic boom to everyone except the flyer. The
    flyer's client draws its own versions.
- **`wind_status` (S2C)** carries the cooldowns, the channel state, and two one-off events: the Tailwind dash, and
  "stop flying". The client counts the timers itself between updates.
- **Cooldowns** live in a server-side map keyed by UUID. They survive death and relogs, but not a server restart.
- **Camera effects** are purely visual (`WindCamera`). `CameraMixin` nudges the first-person view rotation without
  changing the player's aim. `AbstractClientPlayerMixin` adds up to +15% FOV with cruise speed, scaled by the
  FOV-effects slider.
- **The superman pose** (`WindPose`) is built from three mixins:
  - `AvatarRendererMixin` puts the eased pose amount on the render state.
  - `LivingEntityRendererMixin` turns the body to the head's yaw, then tips it about its middle by `-90 - pitch`.
  - `ModelFeatureRendererMixin` poses the limbs: main-hand fist forward, other arm swept back, legs trailing, head
    looking ahead.

  The pose shows for CRUISE and SONIC, and it's the same for every player.
- `LocalPlayerMixin` stops Jump from deploying an elytra while wind-flying.

### When flight ends
Flight ends when you:
- press R
- sink onto the ground in HOVER
- die
- start riding something, or sleep
- get your eyes under water
- stand in lava deeper than 0.4 blocks
- start elytra gliding
- lose the power
- respawn or change dimension

You keep your momentum. The fall that follows is harmless until you land.

## Tuning constants
**Flight** (`client/power/wind/WindFlightController.java`; speeds in blocks/tick, where 1 b/t = 20 m/s):

| Constant | Value | Meaning |
|---|---|---|
| `TOP_SPEED` | 2.5 | top cruise speed (50 m/s) |
| `SONIC_ENTER` / `SONIC_EXIT` | 0.80 / 0.74 | SONIC starts at and ends below these shares of the top speed |
| `HOVER_SPEED` | 0.55 | WASD speed while hovering (creative flight is about 0.55) |
| `HOVER_VERTICAL` | 0.42 | rise and sink speed |
| `HOVER_RESPONSE` | 0.16 | how fast hover velocity follows input, per tick (acceleration and drag feel) |
| `HOVER_BOB` | 0.012 | idle bob speed (about ±0.12 blocks) |
| `CRUISE_ENTER_SPEED` | 0.8 | speed when CRUISE starts |
| `CRUISE_ACCEL` / `CRUISE_ACCEL_TAPER` | 0.05 / 0.6 | acceleration per tick with Sprint held, tapering by 60% toward the top |
| `CRUISE_DECEL` | 0.06 | slow-down per tick with Sprint released and the speed unlocked |
| `CRUISE_BRAKE` | 0.15 | slow-down per tick while holding Back |
| `CRUISE_TURN_SLOW` / `CRUISE_TURN_FAST` | 0.35 / 0.12 | how fast velocity follows your look, at low and at top speed (lower means more drift) |
| `TAKEOFF_BOOST` / `AIR_TAKEOFF_BOOST` | 0.9 / 0.5 | upward kick on take-off |
| `DOUBLE_TAP_TICKS` | 7 | double-tap window for Jump |
| `IMPACT_SPEED` | 0.6 | horizontal speed lost in one bump that counts as a crash |

**Camera** (`WindCamera`): `MAX_FOV_BOOST` = 0.15 and `MAX_SHAKE` = 3°. The boom shake is 2.2° over 14 ticks and the
take-off shake is 0.8° over 8 ticks; both are set in `WindFlightController`.

**Pose** (`WindPose`): `EASE_IN` = 0.12 and `EASE_OUT` = 0.18 per tick.

**Abilities** (`power/wind/WindPower.java`): `TAILWIND_*`, `BARRIER_*` and `ZEPHYR_*`. Cooldowns and the 40-tick
maximum channel are in `WindAbility`.

## Testing
1. `/power set @s wind`. In survival, crashing at speed hurts (see Impacts); creative takes no damage.
2. **Flight:**
   - Press R, or sneak and double-tap Space. You should pop up and hover.
   - Use WASD, Space and Shift to move. Sink to the ground to land.
3. **Cruise:**
   - Hold Ctrl (Sprint). The speed bar fills, and at the gold tick you get the boom, shake and flash.
   - Let go and you coast back down to HOVER.
   - Tap Shift during CRUISE to see LOCKED. Tap S to brake.
4. **Collisions:** fly into a wall at speed. You should stop with a thud and take no damage.
5. **Abilities:**
   - Hold G, V or B for different lengths and check the buff durations in the inventory.
   - Gale Barrier: have a skeleton shoot at you, or `/summon minecraft:skeleton`, and check that the arrows are blown
     back.
   - Zephyr's Breath: stand near a second player and check that they get the buffs too.
6. **Multiplayer:** on a dedicated server, a second client should see the trail, hear the boom, see the superman pose,
   and not see the flyer rubber-banding or get them kicked for flying.

## Known limitations and upgrade ideas
- Toggle-sprint keeps Sprint "held", so CRUISE never coasts down on its own. Use S to brake, or R.
- Hits and knockback don't move you while you fly, because the flight overrides velocity every tick.
- Upgrade idea: impact damage to blocks or mobs you ram, and a ground-slam landing.
- The pose has no banking or roll on turns, and HOVER has no pose (only vanilla animations). Upgrade ideas: roll from
  the turn rate, legs dangling in HOVER, and a cape flutter.
- The flight can be a little jittery for other players at SONIC speed, because they see it through vanilla's entity
  interpolation.
- Cooldowns aren't saved across server restarts.
- The HUD chips use vanilla item icons (feather, wind charge, ghast tear, breeze rod). The only custom art is the Gale
  Heart, from `tools/gen_wind.py` (0.2.0).

## Impacts (survival only)
- **Reporting:** the flyer's client reports a crash, meaning a wall or the ground stopped more than `IMPACT_SPEED` of
  its speed (1.5× that for the ground). This goes in the `wind_impact` packet.
- **Damage:** the server deals `(lost − safe speed) × 10`, where the safe speed is `IMPACT_SAFE_WALL` 0.5 for walls and
  `IMPACT_SAFE_GROUND` 0.8 for the ground.
  - Lost speed is clamped to 3.0 b/t, and impacts are at least 6 ticks apart.
  - Walls count as "flew into a wall" (like an elytra crash) and the ground counts as a fall.
  - Rough damage: cruising (1 b/t) into a wall is about 5; sonic (2.5 b/t) into a wall is about 20.
  - Creative and spectator players are never hurt.
- **Falls:** fall damage is waived only while flying. When flight ends in mid-air, a fall counts from that moment.

## Hover ragdoll (`WindHoverSway`)
- While hovering there's no walk cycle. The body hangs from about the shoulders (`PIVOT` 0.85 of its height) and swings
  opposite to its motion, like a pendulum.
- It's a spring-damper, the same approach as the kitchen's corpses: `SPRING` 0.16, `DAMPING` 0.86. Lean is
  `LEAN_PER_SPEED` 1.1 rad per b/t, capped at `LEAN_MAX` 0.45 rad; rising or sinking adds `LEAN_PER_RISE` 0.5.
- Arms and legs hang limp and trail the swing.
- Other players' sway is read from their synced flight state and their per-tick movement.
- The cruise "superman" pose eases in over it as you speed up.

## Realism pass (0.1.2)
- **Hunger:** flying costs `FLIGHT_EXHAUSTION_BASE` 0.03 + `FLIGHT_EXHAUSTION_PER_SPEED` 0.03 × speed per tick. For
  comparison, sprinting is about 0.028.
  - At food ≤ `CRUISE_MIN_FOOD` 6 you can't cruise, and your lock drops. You can still hover.
- **Turning:** the flight path turns toward your look at most `CRUISE_TURN_RATE_SLOW` 7°/tick when just cruising and
  `CRUISE_TURN_RATE_FAST` 1.2°/tick at top speed.
  - Turning bleeds `CRUISE_TURN_DRAG` 0.5 of your speed per radian.
  - Gravity acts along the path (`CRUISE_GRAVITY` 0.035): dives speed you up, climbs slow you.
- **Crashes:** damage is `IMPACT_DAMAGE_K` 8 × (lost² − safe²). That's about 6 at cruising speed and about 48 at sonic.
- **Tailwind on the ground:** a gust blast instead of the buff.
  - It hits a 35° cone, 6–11 blocks long, for 4–10 damage (falling off with distance).
  - Knockback is strong, and projectiles in the cone are blown away.

## Fall damage (0.1.2, by request: fall damage always applies outside creative)
- There's no fall-damage immunity while flying; the old `PlayerFallMixin` is gone.
- Like an elytra, a controlled descent slower than `GLIDE_SAFE_SINK` 0.5 b/t doesn't build up fall distance. Diving
  into the ground, or turning flight off high up, is a normal fall.
- The `wind_impact` packet is now walls only, since the ground is judged as a fall.
- Core rule for every power: fall distance is measured from your highest point. Moving up resets it.

## Turning limits, the tumble, charged Tailwind (0.1.3)
- **Turning:** the turn rate falls with the square root of the speed fraction, from 7°/tick down to `CRUISE_TURN_RATE_FAST`
  0.7°/tick at top speed.
- **Losing control:**
  - At ≥ `TUMBLE_MIN_FRACTION` 35% of top speed, looking further off your flight path than the limit throws you into
    a TUMBLE. The limit is `TUMBLE_ANGLE_SLOW` 110° at that speed, down to `TUMBLE_ANGLE_FAST` 60° at top speed.
  - The tumble lasts `TUMBLE_TICKS` 40 (2 s) with no control. Momentum and gravity carry you, with drag
    `TUMBLE_DRAG` 0.975. Falls count.
  - It's drawn (`WindTumble`) as an end-over-end ragdoll flip with flailing limbs.
  - Afterwards you recover into a HOVER in the recovery pose: torso pitched 35° forward, head up, arms swept back,
    right knee up and left leg back. It's held for `RECOVER_TICKS` 30.
  - Landing mid-tumble ends the flight; that's a crash.
  - `WindFlight.TUMBLE` is synced so others see it.
- **Tailwind:**
  - It charges for up to 5 s (`WindAbility.maxChannel`; the others stay at 2 s).
  - On the ground the gust scales with the charge: range 6→12, point-blank damage 4→57 (charge², TNT's 57 at full),
    following TNT's falloff `(impact² + impact) / 2`, and knockback 0.6→1.8.
  - Walls block it (line of sight).
  - A full charge (≥ 95%) adds an explosion effect and uses explosion damage, so Blast Protection counts. Blocks are
    never broken.
  - Cooldown is 20 s → 180 s by charge². The HUD shades against each cooldown's actual length.

## Unlock order (0.1.4)
- Natural awakening: R (flight), then V and B, with G (the charged Tailwind, a TNT blast at full charge) last.

## 0.2.0: Ascension & Corruption
Nothing is explained in game. The only hints are the HUD weather glyph, a chime when a breeze kill counts, and one
whisper in the action bar.

### Weather governs the wind (`power/wind/WindWeather.java`)
- `WindWeather.factor(player)` is computed on both sides (each keeps its own eased value per player).
  - It moves toward its target by `EASE_PER_TICK` 0.01 per tick, so 0.6 → 1.3 takes 3.5 s.
- Targets, checked at the eyes:

  | Situation | Constant | Factor |
  |---|---|---|
  | Nether, End, or any dimension without weather (`!canHaveWeather`) | `OTHERWORLD` | 0.85 |
  | No sky overhead (underground, roofed) | `SHELTERED` | 0.85 |
  | Rain or snow falling on you | `RAIN` | 1.0 |
  | Rain or snow falling on you during a thunderstorm | `STORM` | 1.3 |
  | Clear sky, day (`isBrightOutside`) | `CLEAR_DAY` | 0.6 |
  | Clear sky, night | `CLEAR_NIGHT` | 0.85 |
  | Stormbound (corrupted), anywhere | `STORMBOUND` | 1.3 |

  A dry biome (desert, savanna) during a storm counts as a clear sky, because nothing is falling there.
- **What it scales:**
  - Flight: top cruise speed is `TOP_SPEED` × factor (×0.75 when Stormbound), and the cruise acceleration scales with
    it. SONIC is still 80% of the current top speed, so the boom happens in any weather.
  - Tailwind: buff duration and dash.
  - Ground gust: damage and knockback.
  - Gale Barrier: duration and push.
  - Zephyr's Breath: durations.
  - Hover speed is not scaled.
- **HUD:** a 7×7 glyph sits at the right end of the flight readout's row, above the chips:
  - sun, moon, roof, diamond (otherworld), rain cloud, storm cloud (flickers), or a lone purple bolt when Stormbound.
  - A 1-px line under it shows the eased factor as a share of 1.3, so you can watch it slide.

### Storm corruption (`power/wind/WindStorm.java`)
- **The meter fills** only while the sky is `STORM` (a real thunderstorm with rain on you) and you're "active":
  - flying, channelling, or with a Gale Barrier up, or within `ACTIVE_AFTER_CAST` 100 ticks of a cast.
  - It gains +1 per `ACTIVE_TICKS_PER_POINT` 36 active ticks, through `Fate.addCorruption`. 100 points is 3 minutes.
- **The meter drains** at -1 per `DRAIN_TICKS` 300 (15 s) while not under a storm.
- Creative players are spared, because `Fate.addCorruption` ignores them.
- **Stormbound Wind (corrupted):**
  - Every channelled cast (G/V/B) and every Cyclone Lance calls down lightning, and thunder rolls at the caster
    (`CAST_THUNDER_VOLUME` 3). Flight toggling (R) is not a cast. Bolt targets:
    - Ground gust: where the blast lands (the first block along the look, or the cone's tip).
    - Tailwind in the air: where you launched from.
    - Gale Barrier: the nearest hostile within 6 blocks, or you.
    - Zephyr's Breath: the nearest hostile within `ZEPHYR_BOLT_SEEK` 12, or 6 blocks ahead.
    - Lance: see below.
  - **The lightning is a visual-only `LightningBolt` plus our own damage.** This is the chosen fire rule.
    - The bolt is real (render, thunder, sky flash; it powers lightning rods and scrapes copper). It has its cause set
      to the caster.
    - It never places fire blocks, so bases don't burn.
    - Our damage is `LIGHTNING_DAMAGE` 5, of the vanilla `lightning_bolt` type, credited to the caster. It reaches
      vanilla's 3-block box and hits everything except the caster, who is never hurt. Victims burn for
      `LIGHTNING_BURN_TICKS` 80.
    - Vanilla `thunderHit` side effects (creeper charging, pig → piglin, villager → witch) don't happen.
  - Gales hit `STORMBOUND_GALE_MULTIPLIER` 2× harder: the ground gust's damage and knockback, and the barrier's push.
    On top of the storm's 1.3, a full ground gust reaches about 148 point-blank.
  - The weather always counts as 1.3.
  - Costs:
    - -25% movement speed: a transient attribute modifier, `jaymospowers:wind_stormbound_slow`. It's re-checked every
      tick and removed on purification or losing the power.
    - -25% top cruise speed (`STORMBOUND_CRUISE_MULTIPLIER`).
  - **What others see** (client-side, from the synced Fate state, in `WindAura`):
    - dark purple dust motes and electric sparks around the body
    - a faint crackle (a quiet, high-pitched lightning impact) about every 2.5 s
    - In first person you see fewer motes and no sparks.

### The cure: Eye of the Storm
- While Stormbound, keep the flight state HOVER at Y ≥ `EYE_MIN_Y` 192 in the Overworld while `isThundering()`. Hold
  it for `EYE_TICKS` 1200 (60 s) continuous ticks, without channelling or casting. Then `Fate.purify` runs, which is
  permanent.
  - Any break resets it: leaving HOVER (cruising counts), dropping below 192, the storm ending, or a cast or channel.
- **Hint:** the first time per storm that a Stormbound user flies while it's thundering, the action bar whispers *"The
  storm is quieter above..."* once.
- **Progress** shows only as calm:
  - The `jaymospowers:wind_calm` attachment (0–20, synced to all) thins the static motes and sparks.
  - The crackle softens (its volume goes with calm²).
  - There's no bar.

### Ascension: the Tempest (`power/wind/WindTempest.java`, `WindGaleHeart.java`)
- **Breeze kills:**
  - A kill counts when the killer has wind, `Fate.canAscend` holds (all 4 abilities mastered, not ascended), and the
    killer is in wind flight or was within `FLEW_RECENTLY_TICKS` 60 (3 s).
  - Credit goes by `DamageSource.getEntity()`, so deflected projectiles count.
  - The count is stored in the persistent attachment `jaymospowers:wind_breeze_kills`. It's kept through death and
    cleared when the power is lost.
  - Each counted kill gives a faint amethyst resonance that rises in pitch.
- **The Gale Heart:** every `BREEZES_FOR_HEART` 5th counted kill (5, 10, ...), so a lost heart can be earned again,
  the Breeze drops a Gale Heart (`jaymospowers:wind_gale_heart`).
  - The drop comes with an end-rod puff and a low breeze death sound.
  - The heart is Rare, stacks to 1, has two cryptic lore lines, and isn't in any creative tab.
- **Using the heart** (right-click):
  - With wind and `Fate.canAscend`: it's consumed and `Fate.ascend` runs.
  - For anyone else: a puff of wind and a hollow sigh, a 2 s item cooldown, and the heart is kept.
- **Tempest form:**
  - A swirl of cloud puffs spirals up around the body, with a small gust every 0.5 s. It's drawn client-side for every
    ascended wind player within 48 blocks; your own swirl is hidden in first person.
  - Flight costs no hunger.
  - `TEMPEST_TURN_BONUS` 1.4: the cruise turn rate and the tumble angle limits are 40% more forgiving (the angle is
    capped at 179°).
- **Cyclone Lance** (`onUltimate`, C):
  - An instant line from the eyes along the look direction, `LANCE_RANGE` 32 blocks. Solid blocks stop it.
  - Every living entity (and projectile) within `LANCE_RADIUS` 0.6 of the line is hit.
  - Damage is `LANCE_DAMAGE` 14 × max(1, weather), so 14 to 18.2. It never drops below 14 and has no randomness.
    - The damage type is `jaymospowers:wind_lance` (`data/jaymospowers/damage_type/wind_lance.json`, scaling
      "never"). It's tagged `minecraft:bypasses_armor` (`data/minecraft/tags/damage_type/bypasses_armor.json`,
      `replace: false`).
  - Knockback is `LANCE_KNOCKBACK` 1.6 along the line, plus `LANCE_LIFT` 0.25. Knockback resistance reduces it.
  - Visuals: a double helix (cloud plus cloud, or cloud plus electric sparks when Stormbound) at 2 points per block,
    small gusts along it, and a gust burst at the tip.
  - Sound: riptide III, the breeze wind burst, and a wind-charge burst at the tip.
  - `LANCE_COOLDOWN` 120 (6 s). The HUD chip (breeze rod icon, C key label) appears right of the B chip once you've
    ascended.
  - **Stormbound:** exactly `LANCE_MAX_BOLTS` 3 bolts fall along the lance. They land on the first living targets
    hit, and the rest are evenly spaced along the line. The bolts land before the lance's damage, so the lance's 14
    still lands in full through the hurt cooldown.

### Testing (all as a gamemaster, in survival unless noted; `/power set @s wind` first)
- **Weather:**
  1. Run `/weather clear` and `/time set noon`, then `/powerdebug wind weather`. Expect factor 0.6 and the sun glyph.
  2. Run `/time set midnight`: expect 0.85 and the moon.
  3. Go under a roof: the roof glyph.
  4. `/weather rain`: 1.0. `/weather thunder`: 1.3.
  5. Watch the line under the glyph slide over about 3 s, and cruise speed rise and fall with it.
- **Corruption:**
  1. In survival, with `/weather thunder` in a rainy biome, fly around.
  2. Check `/power get @s`: the meter rises about +1 every 1.8 s.
  3. `/weather clear`: it drains -1 every 15 s.
  4. To skip ahead, run `/power corrupt @s`.
- **Stormbound:**
  - Cast G, V and B and check that lightning falls and you take no damage. `/powerdebug wind strike` drops a bolt
    where you look.
  - You walk slower and cruise slower.
  - A second client sees purple motes and sparks.
- **Cure:**
  1. While Stormbound, run `/weather thunder` and fly up to Y ≥ 192 in the Overworld. The whisper shows once.
  2. Hover for 60 s without casting. The static thins as you go. `/powerdebug wind calm 55` jumps ahead; it only holds
     while you stay in the eye.
  3. You're purified. `/power corrupt @s` should now fail.
- **Ascension:**
  1. Have all abilities mastered (`/power set` gives that, or `/power level @s 4`).
  2. `/powerdebug wind breezes 4`, then kill a Breeze (`/summon minecraft:breeze`) while flying or within 3 s of
     landing. A Gale Heart drops.
  3. Right-click it: you ascend.
  4. Another power, or an already-ascended wind, gets a gust and keeps the heart.
  - To skip all of this, run `/power ascend @s`.
- **Lance:**
  - Press C at a line of mobs: all of them on the line take 14 (even in netherite), and the HUD chip counts down 6 s.
  - Stormbound: 3 bolts.
- `/powerdebug wind cooldowns` clears every cooldown.

### Risky or untested at runtime
- **The bypasses-armor tag file is at `data/minecraft/tags/damage_type/bypasses_armor.json`.** If another branch adds
  the same file, merge the `values` arrays.
- The visual-only lightning: if a visual-only bolt is somehow treated differently on the client in 26.3, players won't
  see it. The server side was checked against the decompiled source.
- `precipitationAt` on the client relies on the client's heightmap and biome data. Under leaves, the canopy counts as
  shelter (0.85).
- Weather and sky darkness (`isBrightOutside`) are synced to clients by vanilla. On a dedicated server the client's
  glyph and flight factor may lag the server's by a tick or two. Ability strength is always the server's value.
- The Gale Heart drop uses `spawnAtLocation` from the `AFTER_DEATH` event. It drops even when `doMobLoot` is off.
