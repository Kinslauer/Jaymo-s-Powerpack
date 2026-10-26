# Wind Control

Superhero flight with momentum, a sonic boom, and three wind buffs that you charge by holding a key. The flight's
feel is inspired by the "Viltrumite Flight" mod. That mod is All Rights Reserved, so none of its code or assets are
used here. All sounds and particles are vanilla.

## Controls

| Key (default) | What it does |
|---|---|
| **R** (Ability 1) | Toggle flight on or off. Taking off from the ground gives an upward burst. |
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
| `IMPACT_SPEED` | 0.6 | horizontal speed lost in one bump that counts as a crash |

**Camera** (`WindCamera`): `MAX_FOV_BOOST` = 0.15 and `MAX_SHAKE` = 3°. The boom shake is 2.2° over 14 ticks and the
take-off shake is 0.8° over 8 ticks; both are set in `WindFlightController`.

**Pose** (`WindPose`): `EASE_IN` = 0.12 and `EASE_OUT` = 0.18 per tick.

**Abilities** (`power/wind/WindPower.java`): `TAILWIND_*`, `BARRIER_*` and `ZEPHYR_*`. Cooldowns and the 40-tick
maximum channel are in `WindAbility`.

## Testing
1. `/power set @s wind`. In survival, crashing at speed hurts (see Impacts); creative takes no damage.
2. **Flight:**
   - Press R. You should pop up and hover.
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
  - Damage climbs from `LANCE_DAMAGE` 6 (3 hearts) at weather 1.0 or below to `LANCE_DAMAGE_MAX` 12 (6 hearts) at
    `LANCE_WEATHER_TOP` 1.3 (a thunderstorm), straight-line between (14 × max(1, weather) before 0.8.27). No randomness.
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

## 0.8.0: Tempest Kick, varied every time (`WindKick`, `WindPose` KICK branch)
- **The reference:** a manga high roundhouse. The leg is so fast it's mostly the white crescent; the body sells it.
- **Each kick rolls** (server, synced in `jaymospowers:wind_kick`, now a `WindKick.Kicking(start, left, seed)` record
  with a codec and a stream codec):
  - a **side**: which leg kicks. It mirrors everything: the leg, the lean, the twist, and the crescent (the snap's
    swing arc comes round from the kicking side, and the flying crescent is tipped low on that side).
  - an **arm variant** (`seed & 3`): 0 the reference (the arm on the leaning side flung up and back over the head,
    the kicking side's arm pulled down across the hip); 1 mirrored; 2 both arms flung wide and high; 3 high arm up,
    low arm swung back behind. The rest of the seed jitters the lean, the twist, the leg's height and the arms.
- **Timing** (`KICK_TICKS` 9 → 11): `WINDUP_TICKS` 3 of wind-up (the lean eases in, the knee chambers, a breeze
  inhale), the **snap** on the hit tick (the leg whips out to head height and round across the front, the swing arc
  and the sweep sound, and the crescent leaves the foot), then follow-through and recovery to tick 11.
- **The lean** is done on the whole model in the `PoseStack` (`WindPose.kickLean`, from `setupRotations`), pivoting
  on the standing foot: tipped `KICK_LEAN` 30° (±5) away from the kicking leg, `KICK_LEAN_BACK` 8° back, twisted from
  `KICK_TWIST_WINDUP` -12° to `KICK_TWIST_THROUGH` 28°, and up `KICK_RISE` 0.07 on the ball of the foot. The head turns
  back against the twist and levels against half the lean, so it stays on the target.
- The leg's three key poses are `LEG_CHAMBER_*`, `LEG_APEX_*` and `LEG_THROUGH_*` (model radians; mirrored by side).
- The crescent itself is unchanged in range, damage and speed; it now starts on the hit tick, following the kicker
  until then.

## 0.8.0: Cyclone (hold X; `WindCyclone`, client `WindCycloneClient`, `WindCyclonePose`, `WindCycloneSound`)
- **Key:** the power skill key (X, `key.jaymospowers.web_symbiote`). A **tap** is still the Tempest Kick; **holding**
  it `WindCyclone.HOLD_TICKS` 8 (0.4 s) forms the Cyclone, which lasts while X stays down.
- **Tap or hold is decided on the client** (`WindCycloneClient`), because X's mapping and its old kick send live in
  `WebSymbioteClient` (Web's file). For a wind user, X's clicks are consumed at `START_CLIENT_TICK`, before the
  symbiote's `END_CLIENT_TICK` handler can see them, so that old send never fires for wind. The client sends
  `WindKick.Kick` on a release before the threshold (the kick now goes off on release, not on press), and
  `wind_cyclone` (`WindCyclone.Hold(down)`) at the threshold and on release. A press and release inside one tick
  counts as a tap. Holding past the threshold with no Cyclone available does nothing.
  *Lead:* once merged, the kick branch in `WebSymbioteClient`'s X handler is dead code for wind users and can go.
- **Mastery (decided):** the Cyclone needs `MIN_MASTERY` 1 unlocked ability, not everything mastered like the kick.
  It levels with you anyway: radius `RADIUS_MIN` 3 → `RADIUS_MAX` 6 and height `HEIGHT_MIN` 6 → `HEIGHT_MAX` 12, in
  even steps from 1 to 4 unlocked abilities (creative and `/power set` count as all 4).
- **Starting** needs you on the ground, not flying, not carrying or carried, not riding, and off cooldown; otherwise a
  soft breeze sigh. Flight (R) can't be toggled while it lasts; anything that ends it (death, taking flight, being
  picked up, a dimension change, losing the power, logging out) stops it like letting go.
- **Stance** (`jaymospowers:wind_cyclone`, a synced `Stance(start, left)`; `left` rolls which knee goes down): a
  superhero landing. `WindCyclonePose` lowers the whole model `DROP` 0.45 in the `PoseStack`, turns it `TWIST` 10°
  toward the raised arm, and tips it `HUNCH` 40° forward about the hips. The legs are turned back against the tip:
  each is set at the angle that puts its end on the ground (`acos((HIP - DROP) / LEG)`), the kneeling one back (the
  knee on the ground) and the other forward (the foot planted). One arm sweeps up and back over the shoulder
  (`HIGH_ARM_*`), the other reaches down by the ground (`LOW_ARM_*`), the head bows (`HEAD_DOWN`), and the arms
  tremble. It drops in fast (`DROP_IN`) and rises slower (`RISE_OUT`). Other wind poses are off while it shows.
- **Rooted:** while it lasts, `MOVEMENT_SPEED` and `JUMP_STRENGTH` get a -100% modifier (`wind_cyclone_root`), and
  the client keeps no sideways momentum.
- **The tornado** (server particles, so everyone sees it): `RINGS` 6 rings of cloud turning at `TURN_RATE`, from
  `FOOT_SHARE` 35% of the radius at the foot to the full radius at the top, with gusts and dust at the foot; it rises
  to full size over `FORM_TICKS` 8. Corrupted, it carries purple storm dust and crawling sparks, with distant
  thunder. Sound: the landing thump, a roaring loop on every client in range (`WindCycloneSound`, the elytra wind,
  low), a breeze whirl every `ROAR_INTERVAL` 16 ticks, and a burst on release.
- **Caught creatures** (anything alive in the funnel but you; 0.8.6: a "get off me", not a damage move): each one is
  swept into an orbit round you at `SPIN_SPEED` 0.45, spiralling out from `ORBIT_START` 1.8 blocks to
  `ORBIT_END_SHARE` 65% of the funnel's radius and rising to `RISE_HEIGHT` 4 blocks above your feet (never above the
  funnel) over `ORBIT_TICKS` 40 (2 s). Then it's thrown: `FLING` 1.4 outward, `FLING_UP` 0.5 up, bent `FLING_SPIN`
  40% along the spin like a sling, with a gust and a wind-charge burst. A thrown creature can't be caught again for
  `RECATCH_TICKS` 30. Anything that leaves the funnel early starts its orbit over. Knockback resistance scales all
  of it (full resistance: it stays put and only takes the damage). Letting go throws whatever is still in orbit.
- **Damage:** light, while orbiting.

| Tier | Others | You (suffocation) |
|---|---|---|
| Base | `DAMAGE` 1.5 every `DAMAGE_INTERVAL` 10 ticks (wind-charge damage): 6 over a full orbit | `SELF_DAMAGE` 1 every `SELF_INTERVAL` 40 ticks |
| Ascended | `DAMAGE_ASCENDED` 2.5 every 10 ticks: 10 over a full orbit | 1 every `SELF_INTERVAL_ASCENDED` 80 ticks |
| Corrupted (and ascended + corrupted) | 2.5 every 10 ticks, as **shock** (`LIGHTNING_BOLT` damage type) with sparks | 1 every 80 ticks |

  Your own damage is `damageSources().inWall()` (suffocation: it ignores armour). Any Resistance (Gale Barrier's
  included) is made up for, and if a fresh hit's invulnerability blocks it, it lands on the next tick instead.
  Creative takes none. The corrupted shock has a `// FLAMES:` comment where `Flames.touch(...)` goes after merging.
- **Costs:** `EXHAUSTION_PER_TICK` 0.06 hunger a tick while held (outside creative), and `COOLDOWN` 60 ticks after
  letting go.
- **HUD:** a Cyclone chip (a guster banner pattern) after G / V / B with the bound key and the cooldown; it pulses
  while the Cyclone lasts, and "Cyclone" shows over the crosshair with a swirling band.
- **Queries for synergies:** `WindCyclone.isActive(player)`, `radius(player)`, `height(player)`, `isAvailable(player)`.

## 0.8.0: carrying while flying (see `docs/carry.md`)
- A Wind user carrying someone can take off and fly as usual, at `Carry.FLIGHT_SPEED` ×0.8 of the top cruise speed
  (`WindFlightController`) and `Carry.FLIGHT_HUNGER` ×1.3 the flight hunger (`WindPower`).
- In the air any of the four holds can be rolled; take-off rolls a new one, and landing with a flight-only hold
  (shoulder grab, over the back) switches to fireman's or bridal.
- A cruising carrier's tilt is shared with the body they hold (`WindPose.tiltLike`), so the two lie along the flight
  together. While carrying, the superman pose and the hover sway leave the arms to the hold.
- Being carried ends your own flight (`WindFlight.canFly` is false while carried), and a carried wind user shows no
  wind poses.

### Testing (0.8.0)
1. Tempest Kick (`/power set @s wind`, all abilities): kick a dozen times in third person (F5). Both legs should
   appear, the body tipping hard away from the kicking leg about the planted foot, the arms in four different
   arrangements. The swing arc comes round from the kicking side on the hit tick (about 0.15 s after the key), then
   the crescent flies, tipped low on that side.
2. A second client sees the same side and arms as the kicker.
3. Carry someone while flying: cruise speed tops out lower, hunger drains faster, and the held body lies along the
   flight with you.
4. Cyclone (`/power set @s wind`): tap X, the kick still fires (on release). Hold X: after 0.4 s you drop into the
   superhero landing (F5: knee on the ground, other foot planted ahead, hunched, one arm up and back, other hand by
   the ground) and the funnel rises. WASD and Space do nothing. Let go: the funnel bursts, the HUD chip shows a 3 s
   cooldown.
   With a few zombies close by: each is swept round you, spiralling up and out to about head height over 2 s,
   takes a few small hits, and is slung away; it walks back and gets caught again after 1.5 s.
5. Summon a few zombies and pigs nearby: they're spun round you and lifted a little, taking 4 a second (base). In
   survival you take ½ heart every 2 s, even with Gale Barrier up. `/power ascend @s`: 4 every 0.5 s to them, ½ heart
   every 4 s to you. `/power corrupt @s`: sparks on each hit, lightning damage, purple dust in the funnel.
6. `/power level @s 1` (or a fresh awakening with one unlock): a 3-block funnel; at 4 unlocked, 6 blocks. At 0
   unlocked, holding X does nothing.
7. A second client sees the stance, the funnel and hears the roar; mobs on the server take the damage.

## 0.8.3: heavier Tempest Kick, the cold, heavy weapons, fumbling

### Tempest Kick: more weight (`WindKick`, `WindPose` KICK branch)
- **Timing:** `WINDUP_TICKS` 3 → 6 (the lean builds and the leg chambers), the snap through the arc (`SNAP_TICKS`
  1.5) with the crescent leaving on the hit tick (the server's crescent delay is the same wind-up), then about 8 ticks
  of follow-through and recovery (`RECOVER_TICKS`). `KICK_TICKS` 11 → 16. The lean holds `KICK_LEAN_HOLD` past the
  snap, then eases out.
- **Bend at the hip:** on top of the whole-body lean about the standing foot, everything above the hip (torso, head,
  arms) bends `KICK_HIP_BEND` (30°, ±`KICK_HIP_BEND_JITTER`) further away from the kicking leg, pivoting at the hip
  (`WindPose.hipBend`: each upper part's pivot swings round the hip and the part tips with it, so the arms and head stay
  attached). The neck turns back `KICK_HIP_HEAD_LEVEL` of it. With the whole-body lean, the leg's apex
  (`LEG_APEX_X` -1.95 → -2.05) is level with the head at the snap.
- `Kicking` gains a `windup` field (the cold's longer wind-up); `Kicking.length()` is the whole kick's length. In a long
  wind-up the pose holds the chambered leg (trembling, `KICK_HOLD_SHAKE`) and the kick's beats run as usual after it.

### The cold slows the wind (`WindCold`)
- Where it would snow at the player's eye (`Biome.coldEnoughToSnow(pos, seaLevel)`: the biome's temperature at that
  height, weather or not; a snowy mountain top counts), every Wind ability winds up `EXTRA_WINDUP_TICKS` (60) longer and
  cools down `EXTRA_COOLDOWN_TICKS` (60) longer:
  - **Wind-ups:** the channels (G / V / B: the full charge takes 60 ticks more; it's stored per channel and sent to
    the client in `WindNet.Status.channelMax`, so the HUD bar matches), the Cyclone's hold threshold on X (client),
    the Tempest Kick's wind-up.
  - **Cooldowns:** G / V / B, the ground Gust, Call Lightning, Cyclone Lance, the Cyclone, the Tempest Kick.
- **HUD:** a pale-blue frost mark (a snowflake) left of the weather glyph while it's cold, a "COLD" tag on the charge
  meter's label, and an action-bar line when the cold first takes hold (at most once a minute).

### Heavy weapons (`WindHeavy`, `mixin/WindPlayerAttackMixin`)
- A Wind user's melee hits with an axe (`#minecraft:axes`) or a mace (`minecraft:mace`; 26.3 has no maces tag) deal
  `DAMAGE_SCALE` ×0.6: the main hit and the sweep (`@ModifyArg` on `hurtOrSimulate` and `doSweepAttack` in
  `Player.attack`). The mace's smash bonus is in the hit's damage, so it's scaled too.

### Fumbling (`WindFumble`)
- Until `Mastery.unlocked` reaches `MASTERED` (3), each Wind ability used (a channel's release, Cyclone Lance, the
  Cyclone) has a `CAST_CHANCE` (8%) chance, and each second of flight a `FLIGHT_CHANCE_PER_SECOND` (2%) one, of the
  main-hand item slipping: thrown ahead as an item entity (`ServerPlayer.drop`, pickup delay
  `PICKUP_DELAY_TICKS`), with a sound and an action-bar line. Never in creative; nothing is destroyed.

### Testing (0.8.3)
1. Tempest Kick (F5, all abilities): slower to chamber (about 0.3 s), the snap, then a visible recovery. The torso,
   head and arms bend sideways at the hip away from the kick, on top of the lean; the arms and head stay on the
   shoulders; the kicking foot comes up to head height at the snap. The crescent leaves at the snap.
2. A snowy biome (`/locate biome minecraft:snowy_plains`) or a snowy peak: the frost mark shows; a G / V / B charge
   takes 3 s longer to fill; cooldowns are 3 s longer; holding X takes 3.4 s to become a Cyclone; a kick holds its
   chamber 3 s before snapping (a second client sees the same). Back in plains, all normal.
3. As a Wind user hit a zombie with an iron axe, then with no power: the Wind hit is about 60% of the other. A mace too.
4. `/power level @s 2` (two unlocked), survival, holding a stack: fly for a minute and use abilities; now and then the
   item is thrown ahead with a line on the action bar. At 3 unlocked, or in creative, it never happens.

### Risky (0.8.3)
- The mixin's two `@ModifyArg` targets must match 26.3's `Player.attack` (checked against the jar); a mismatch crashes
  on start (`defaultRequire` 1).
- `Status` and `Kicking` changed shape: client and server must both be on this build.

## 0.8.23: Tempest Kick as a roundhouse or a dwit chagi (`WindPose` KICK branch)
- **The reference (from the user):** a high side roundhouse (standing leg planted, torso leaning away, leg out level
  with the head, guard up) and a dwit chagi (back kick). Replaces 0.8.0's flung-arm variants.
- **Style** from the seed (`KickPose.back()`, client only): a spinning back kick one time in `BACK_KICK_ONE_IN` (3),
  else a roundhouse. The side (which leg) still mirrors everything. `WindKick.Kicking.arms()` is gone.
- **Roundhouse:** the body pivots on the standing foot (`kickTurn`) away from the kicking leg, `KICK_TURN_CHAMBER` 35°
  by the end of the chamber and `KICK_TURN_APEX` 75° (±8) at the snap, so the kicking hip faces the target; tipped
  `KICK_LEAN` 30° away, `KICK_LEAN_BACK` 6° back, and bent `KICK_HIP_BEND` 20° at the hip. The leg chambers knee-up
  and out (`LEG_CHAMBER_*`), snaps straight out to the side (`LEG_APEX_*`), holds `KICK_LEAN_HOLD`, pulls back to the
  chamber over `KICK_RETRACT_TICKS` and drops. The head turns back by the whole body turn, so it stays on the target.
- **Back kick:** the body spins toward the kicking side, `BACK_TURN_CHAMBER` 110° then `BACK_TURN_APEX` 175° (its back
  to the target), pitches `BACK_PITCH` 30° forward and tips `BACK_LEAN` 8°; the leg tucks (`BACK_CHAMBER_*`) and drives
  straight out behind (`BACK_APEX_*`). The head looks back over the kicking-side shoulder (`BACK_HEAD_TURN`). In the
  recovery the body carries on spinning the same way round to 360°, facing the target again.
- **Guard:** both fists up by the face, turned in toward the chin; the lead (kicking-side) arm higher
  (`GUARD_LEAD_X`, `GUARD_REAR_X`, `GUARD_IN`, `GUARD_REAR_IN`). In the roundhouse the guard turns `GUARD_TRACK` of
  the way back toward the target.
- Timing, the crescent, the swing trail and the cold's held chamber are unchanged.
- **The cold's kick wind-up** (`WindKick.COLD_EXTRA_WINDUP_TICKS`): 20 ticks (1 s) extra, not the 60 the other
  abilities take. While it's held, the server swirls white dust in a rising spiral round the kicker
  (`COLD_SWIRL_*`, thickening as it builds) with snowflakes, and plays a blowing wind (breeze air and whirl) every
  `COLD_SOUND_EVERY` ticks (`WindKick.coldSwirl`). The cooldown's cold extra is unchanged.

## 0.10.17: the quarterstaff rides the gale (`WindStaff`)

Any item whose id ends in `quarterstaff` (the Armory's wooden and iron ones), held by a **wind sorcerer**: a Jaymo's
Kingdoms Sorcerer who picked the air element (0.10.17, Andy: "only wind sorcerers"; the Wind power alone doesn't count).
Kingdoms is read by name (`Classes.of`), so without it nobody qualifies. Server only; Better Combat's
five staff swings play as before, and our own chain of landed swings (one count per swing, broken after
`CHAIN_TIMEOUT` 40 ticks without a hit) picks the gale for each:

1. **Gust** (front twirl): the foe is blown back and lifted.
2. **Wind Blade** (sweep): a blade of air runs 6 blocks ahead and cuts everything in the cone (0.5x attack damage).
3. **Vortex** (whirlwind): everyone within 6 blocks is dragged in toward you.
4. **Piercing Gale** (thrust): the foe is hurled far back along your look.
5. **Downdraft** (overhead slam): everything within 4.5 blocks is thrown up (0.4x attack damage); slammed from the
   air, it bounces you back up too, landing soft.

Every staff hit also leaves a small puff. **Staff Vault** (right click): ride a gust forward and up; sneak + right click
goes straight up. 50-tick cooldown (cold-stretched), and no fall damage for 80 ticks or until you land. The Tempest mode
and the ascension make every gale 1.3x. Tuning at the top of `WindStaff`. Not playtested.

## 0.10.27: daggers ride the gale too (`WindDagger`)

Andy: "wind should get increased proficiency with daggers the same way it does quarterstaves". Same gate as the staff
(a **wind sorcerer**), same shape. Any item whose id contains `dagger`, `knife` or `kunai` (the Armory's daggers,
knives and kunai, Balkon's knives). Better Combat's four dagger swings play as before; our own chain of landed swings
(broken after `CHAIN_TIMEOUT` 30 ticks) picks the gale:

1. **Gale Cut** (slash): a blade of air 4.5 blocks ahead cuts everything in a narrow cone (0.5x attack damage).
2. **Wind Stab** (flip and stab): the foe is driven back and you ride the wind in after it.
3. **Crosswind** (backhand): everyone within 3.5 blocks is swept across your front.
4. **Cyclone Thrust** (double stab): a burst round the foe throws it and anyone within 3 blocks of it up and away
   (0.35x attack damage to those beside it).

Every dagger hit leaves a small puff. **Flurry** (right click, not with the throwable kunai; Andy: "the dagger and
knife instead do the flurry sora's dagger used to do"): Sora's Dagger's winds' Flurry, 10 slashes 3 ticks apart, each
cutting a 5.5-block cone ahead (0.65x attack damage, no hurt cooldown between slashes), carrying you forward 0.38 a
slash and throwing what it hits, then a burst throws everything within 5 blocks out and up. 400-tick (20 s) cooldown
(cold-stretched); it stops if you put the dagger away. The Tempest mode and the ascension make every gale and the
Flurry 1.3x. Tuning at the top of `WindDagger`. Not playtested.

## Beam clash: Wind Whistle against another stream (`core/BeamClash`)

The user: "the wind and smoke X abilities should clash with an effect upon meeting, for a beam battle." Shared with the
Smoke Jet (`docs/smoke.md`), in one class, `core/BeamClash`; each beam registers itself (`BeamClash.register(Beam)` in
`WindJet.init` / `SmokeJet.init`), so another beam (Z Fighter ki) can join the same way. Server-authoritative; the
client half is `client/BeamClashClient`.

- **Meeting.** Two held streams (Whistle vs Smoke/Soot Jet, and also Whistle vs Whistle, Jet vs Jet) lock when the
  users face each other (each look within ~37 degrees of the other, `FACING_COS` 0.8), are 3+ blocks apart and within
  both reaches, and the two stream segments pass within 1.5 blocks (`MEET_DISTANCE`). The knot starts in the middle of
  where the streams overlap (never nearer an end than 20%).
- **While locked.** Each stream ends at the knot (`BeamClash.clamp` in `WindJet.tick`): the Whistle no longer blows the
  jet away, it grinds against it. Both users are rooted (-90% speed, no jump, held in place client-side; the losing
  one's feet slide back) and pay 0.05 hunger a tick on top of the stream's own. Everyone near sees both streams as
  straight beams into a churning knot (each side's wind/smoke spiralling in, sparks, a flash and a ring pulsing out,
  faster the harder they push) and hears a roar and grind there. Light shakes every 8 ticks, harder for whoever is losing.
  Anyone else in the knot (1.6 blocks) is hurt (1) and thrown out every 10 ticks.
- **The struggle.** A balance from -1 to 1 moves the knot along the line between the users. Each side pushes a steady
  0.005 a tick, +60% ascended, +10% per mastered ability, plus a surge from **tapping X** while holding it (release and
  press again inside 6 ticks, `GRACE_TICKS`; +0.006 a tap, fading x0.85 a tick, at most 0.03; taps under 2 ticks
  apart count once). Mashing hard (8 taps a second) beats a steady hold from the middle in about 3.5 s.
  **0.17.1 (Andy: "near impossible to break without a SERIOUS struggle ... overdone"; "factor the player's power
  levels in"):** a tap is +0.009 and the surge fades x0.9, so about 6 taps a second fills it. Each side's **power level**
  is its steady push over 0.005 (ascension, mastery, a ki wave's strength); its taps and surge cap are weighed by
  sqrt(its power / theirs), kept in 0.75-1.35. A Z Fighter's ki wave reads his ki power live (`ZFighterWaveBeam`,
  `CLASH_KI_POWER` 1.0, was 0.3 fixed at firing), so **going Super mid-clash** raises his push at once; a power jump of
  6%+ in a tick also fills his surge, shakes both and tells both ("Your power surges into the clash!"). HUD: a "Clash" panel bottom-right above the chip
  row: the balance bar (your colour from the left, theirs from the right, the knot between), both pushes under it,
  and the time left along its top.
- **Endings.** Knot reaches a user: **overwhelmed**, 6 damage, thrown 1.8 back and 0.55 up; beaten by wind they also
  spin (40 degrees a tick, slowing over 24 ticks); their stream stops and rests 3 s; the winner's stream rolls on.
  **Letting go** (a release over 6 ticks, looking 60+ degrees off, running out of food): the other's stream rolls
  straight through (2 damage, thrown 0.9). **Stalemate** past 160 ticks (8 s): the knot **detonates** (explosion,
  gusts and smoke, 4 damage and a throw for both, bystanders within 6 thrown), both streams stop and rest 5 s.
- Input: the wind client's IDLE phase sends `WindJet.Hold(true)` without sneaking while clashing, and a press-release
  inside one tick sends both; the server routes Hold packets to `BeamClash.input` first while clashing.
- Every number is a constant at the top of `BeamClash` (and the look at the top of `BeamClashClient`). Not playtested.

## 0.15.58: the way into the Aether (`power/wind/aether/WindAether.java`)

Wind is the power that gets you to the Aether, the way Chrono's Fast Forward gets you to the future. Lore: the Aether
scattered remnants of itself across the Overworld (Kingdoms' Aether monoliths and the four Giants).

- **The dimension** `jaymospowers:aether` is datapack plus nine Java features (`com.jaymospowers.aether`, `aether.world`),
  described in `docs/aether.md` ("The dimension"): Aether II's Holy Isles. Dimension y -32 to 384: big lowland islands
  (tops y 95-135, undersides y 6-60, caves and the Hestveil caverns inside), a broken archipelago of islets above y 130
  (two layers, tops near y 170 and 230) with open sky between, a cloudbed far below at y -30..-18. Sixteen biomes (four
  highfields, four magnetic, four arctic, two irradiated, Hestveil, and the unplaced Expanse), the Overworld's day and night,
  trees, ores, lakes, plants and all six aercloud colours. The old four skyroot biomes are gone: an existing world's Aether
  chunks keep their old biome ids (which no longer exist), so use a fresh Aether (delete the world's `DIM-aether` folder,
  or a new world).
- **Up.** A Wind user in flight in the Overworld who climbs to within `ASCEND_MARGIN` (4) blocks of the top build height,
  with ability 4 (B) unlocked (`Mastery.isUnlocked`), rises with every player within `COMPANION_RADIUS` (4). Arrival: same
  x/z standing on bare lowland ground (`aetherTopAt`: the heightmap's top if it is at or below `ARRIVAL_SCAN_TOP` (150), else
  the first walkable ground scanning down from there, so an islet overhead is not picked; the block must be solid and not a
  log or leaves, with two free blocks over it), looked for within `STAND_SEARCH` (3) blocks so a tree's trunk is not picked;
  none there, so a square spiral out in 16-block steps to `SEARCH_RADIUS` (96); none at all, the open air at y 150 (above the
  lowland tops, near y 135) with Slow Falling (`EMPTY_SLOW_FALL_TICKS`). `ASCEND_COOLDOWN` (200 ticks) stops looping. The
  first visit tells the lore (`VISITED`). No Return is stored: the way home is the fall.
- **Home.** Anyone (Wind or not) below `FALL_OUT_Y` (-8; the islands' undersides start at y 6 and the cloudbed floats below
  that line, y -30..-18, as scenery) in the Aether lands in the Overworld at the same x/z,
  `ARRIVE_ABOVE_GROUND` (48) above the ground (capped `ARRIVE_BELOW_TOP` (8) under the top), with Slow Falling for
  `SLOW_FALL_TICKS` (600), plenty to land. Fall damage still applies once it ends. Dying takes you to your own spawn as usual.
  Arsenal standing portals (`ArsenalStanding`) search for safe ground around remembered positions and have no heights of
  their own, so the new dimension needs no change there.
- Tuning: constants at the top of `WindAether`. Not playtested; the dimension needs a fresh world or a datapack reload to exist.
