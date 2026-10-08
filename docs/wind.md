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
- There's no custom art. The HUD chips use vanilla item icons (feather, wind charge, ghast tear), so there's no
  `tools/gen_wind.py`.

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
