# Gravity

Taking the weight out of things, or piling it on. All server-side in `power/gravity/GravityPower.java`. A target's
weight is its `minecraft:gravity` attribute, changed with **transient** modifiers, so nothing sticks if the server stops
mid-ability. The HUD is in `client/power/gravity/GravityPowerClient.java`: chips bottom-right, in Wind's style.

## Abilities
**Unlock order:** R, V, G, B. Crush is charged, so it comes last.

| Key | Ability | What it does |
|---|---|---|
| R (hold) | Lift | Anything under the crosshair (creatures, players, dropped items, carts, corpses...) loses its weight. The reach is 14 blocks, plus 4 per ability mastered, up to 30. It and floats where you point. You hold it at the distance you grabbed it from, clamped to 3–14 blocks. Let go and its weight returns, so it falls, and fall damage is real. It works on players too. Max 6 s, 8 s cooldown. Lifted creatures go limp, reusing Web's ragdoll. Right click on a held dropped item draws it in and into your inventory, as much as fits; the rest drops at your feet. Right click on a held creature (R unlocked) chokes it, held up high, and a second click slams it: it's hauled up, then driven down into the ground 1–3 blocks deep (the chance of 3 grows from 10% by 20% per ability mastered), throwing up debris from the ground around it. Your arm goes overhead and comes down with it. |
| V | Weightless | Toggle. Your gravity drops by 70% and you take 50% less fall damage. It drains hunger and lasts up to 30 s, then a 10 s cooldown (2 s if you switch it off early with V). While it's on, everything within 6 blocks of you is heavy and slowed. Water or an empty hunger bar ends it. |
| G | Gravity Well | A point where you aim, up to 24 blocks, pulls everything within 7 blocks (mobs, players, items) for 4 s. 14 s cooldown. |
| B (hold, release) | Crush | Charges over 3 s. While it charges, everything in the zone you aim at (at the charge's current size) is already heavy and slowed. On release, everything around the aim point is slammed down. Radius scales 2.5→5.5 and damage 4→16 with charge, plus Slowness II, and targets weigh 4× as much for 3 s. Cooldown is 6–20 s, depending on the charge. |
| Z | Immense Field | Once the Well (G) is yours. A dome of heavy gravity (5 blocks round) where you aim (24 blocks), for 60 s: everyone in it but you gets Slowness II and an extra pull down in the air. A Z Fighter trains twice as fast in it (TP and form mastery; his blows count as heavy), and every minute one spends in it gives you a use towards mastery. Z again takes it down. 90 s between fields. (`GravityTrainingField`) |

## 0.3.0 fate
- **Deaths by weight:** a creature you touched with a gravity ability in the last 10 s that then dies from a fall, from
  Crush, or from the Singularity.
- **Ascension:** 30 deaths by weight, which only start counting once every ability is mastered. Whispers come at 10
  and 20.
  - **Event Horizon:** full knockback resistance, projectiles within 3 blocks are bent away, and a ring circles your waist.
  - **Singularity (C):** a black hole at the aim point (up to 32 blocks) for 3 s.
    - It pulls everything within 10 blocks.
    - Every 0.5 s, it deals a guaranteed 3 magic damage to everything within 4 blocks (18 in total).
    - It ends in a 6-damage burst that throws everything outward.
    - 25 s cooldown.
- **Corruption (Collapse):** +2 per death by weight, and +1 per 20 s spent weightless.
  - **Drawbacks:**
    - You weigh 35% more.
    - You take 25% more fall damage.
  - **Upsides:**
    - Crush hits ×1.6 harder, with +1 radius.
    - Wells pull harder.
    - Releasing Lift throws the target where you look.
    - Loose items and small mobs (10 HP or less) drift toward you.
  - Other players see a portal-dark swirl around you.
- **Cure:** spend 10 continuous seconds under Levitation from something else (a Shulker) while collapsed. The first
  time you levitate, you get a one-line whisper.

## 0.8.0: Collapsing Horizon (ascended + corrupted)
- **Skyfall (C) + Weightless:** if Weightless is on when you cast, or you switch it on during the 3 s rise (within
  20 blocks of where you cast), you rise with everything else. You're never slammed and never hurt by it. After the
  rise you hang where it left you, with no weight at all, for as long as Weightless lasts (up to 30 s). Touching down
  on something also ends the hang. When Weightless ends (timeout, V, water, hunger), you drift down at Weightless's
  weight and land without fall damage. Rock flight, flying, gliding, water or lava end the ride.
  `GravitySkyfall.airborne(Entity)` and `GravitySkyfall.riding(ServerPlayer)` let other powers check who is up there.
- **Staggered slam:** nothing comes down together. Each creature and each block drops at its own random moment in the
  1.5 s (`STAGGER_TICKS` = 30) after the rise, and hangs there until its turn. Each creature's landing damage (fall
  height × size × 0.4) is multiplied by its own roll of ×0.6–1.5. Every landing (creature or block) gets its own heavy
  mace thud and a small shake. The sonic boom, the explosion and the big shake come when the last one lands. Cleanup
  waits up to 200 ticks after the stagger window.
- **Orbit:** at most 3 blocks circle you (orbiting plus drifting in). Plain Crush (B) still flings them out in a
  shockwave. **G+B** (Crush released inside your own Gravity Well, the cave-in) now rains them instead. They gather
  over the well, then every 8 ticks one is released. It lines up (in 5 ticks) up to 5 blocks over the nearest
  creature the well caught, staying under any ceiling, and drops straight onto it at 2.2 blocks/tick. Before 0.8 they
  were never released on that path.
- **Soft blocks** (hardness ≤ 0.6 (`BlockCluster.SOFT_HARDNESS`): dirt, grass, sand, gravel, clay, soul sand, snow,
  concrete powder, leaves; plus wool, which is 0.8 but listed as soft) deal at most 1.0 (half a heart). This applies
  to any thrown `BlockCluster` made only of soft blocks, from any power, and to cave-in falling blocks. A chunk with
  any hard block keeps the normal formula.
- **Meteor fire** (`core/Flames`): the falling rock sets alight everything within 2.5 blocks of it for 5 s
  (`TRAIL_FIRE_*`), and the impact sets alight everything within 10 blocks for 8 s (`IMPACT_FIRE_*`): creatures,
  corpses, items, armor stands, arrows; creepers are lit, TNT minecarts go off, primed TNT burns faster. Never you.
  Damage is unchanged.

## 0.8.1: Gravity ignores weight
A Gravity user lightens whatever they hold, at every mastery level (`GravityWeight`):
- **Carrying** (see `docs/carry.md`): with an empty hand, sneak + right-click picks up any living mob but a boss (Ender
  Dragon, Wither, `c:bosses`), an armor stand, something over 4 blocks wide or 6 tall, or something in Lift's grip.
  No carry slowdown at all, players included. A mob bigger than 0.8 × 1.0 blocks is held up **overhead** on raised
  arms, floating and bobbing; a throw from there is a slow, heavy heave. A carried mob's AI is paused.
- **Kitchen carts** (`mixin/GravityCartMixin`, in the new `jaymospowers.gravity.mixins.json`): pulling a cart by
  hand puts no drag on them (`CartEntity.drag` returns 0), and a loaded supply cart can be taken by the shafts and
  kept (`tooHeavyByHand()` reads false for a Gravity puller in `serverHitch` and `interact`). A drag left on from
  before the power came is lifted within a second.
- **Armor weight**: every second Gravity's tick adds up the negative movement-speed modifiers that come from equipped
  armor and held items (and are on the player), and cancels them with counter-modifiers (`gravity_unburden_add`,
  `_base`, `_total`; a multiplying one is countered down to ×0.05 at most). Removed when the power is lost. Nothing
  in the pack adds such weight yet.

## 0.8.25: Pull, shockwave, kneeling, the earthboard, the meteor's wreck
- **Lift (R)** reaches 44 blocks, +4 per ability mastered, 60 at full (`LIFT_RANGE_BASE`, `LIFT_RANGE`). Players are
  easier to catch: their box is widened by `LIFT_PLAYER_SLACK` (0.9) plus `LIFT_PLAYER_SLACK_PER_BLOCK` (0.012) per
  block away (anything else 0.3). While R is ready, four purple corners show round the crosshair (first person) when
  something Lift could take is in reach, brighter and wider on a player (`GravityPowerClient.liftMark`, which runs the
  same `GravityPower.liftableInSight` as the server).
- **Gravity Well (G)** goes off as a bubble (`GravityShockwave`): it swells to 8 blocks over 12 ticks, purple dust
  shell, the ground's block particles blasted up where it meets the ground, purple streaks and dark shards rising.
  As it passes, 24 lumps (rings of 5, 8, 11 at 3, 5.4, 7.6 blocks) of the ground's own blocks (made, never torn out of
  the world) shove up out of the earth, leaning out 20-60 degrees. Each lies its own 3-11 s (`DEBRIS_LINGER_MIN`..`MAX`), Lift can take and
  throw them, then they crumble away a block every 3 ticks; they never drop anything and pocketing one only breaks it.
  The well's pull leaves them alone. Its pull still works as before.
- **Kneeling**: anyone Crush pins, or a Gravity Well holds, gets `GravityPower.KNEEL` (a random style of 4, since when),
  synced to everyone; it's taken off when the weight is (the well's 6 ticks after they leave it). Players and humanoid
  mobs (anything drawn with a `HumanoidModel`) fight it for 18 ticks (knees buckling, trembling, arms shoved up),
  drop in 5, then stay down heaving; they get back up over 14 ticks after (`client/.../GravityKneelPose`). Poses:
  hunched with both hands planted; one knee, forearm on the other; both arms over the head; slumped, head hanging.
- **Earthboard (hold V)** is anyone's once Weightless is unlocked. It tears up a ragged bowl about a creeper's blast
  (`CRATER_RADIUS` 2.8, 2 deep, at most 45 blocks) and rides up to 19 blocks over the ground (`MAX_ALTITUDE`); in the
  top 3 (`STRUGGLE_BAND`) the climb weakens, it shudders and sags, the rider's view shakes, and it sheds more grit.
  Until fully mastered (or ascended, or corrupted: `GravityRockFlight.toggles`) you ride only while V is held;
  after that it's the old toggle. Stepping off, the board drops whole (`BlockCluster.BOARD`: solid, its box as big as
  its blocks) and lies 8 s, then crumbles (30% of its blocks drop, as they were real). Holding V while standing on a
  lying board rides that board again. A V tap now toggles Weightless on release for everyone.
- **Meteor**: the rock bursts apart where it lands (`GravityMeteor.wreck`) into 4-30 lumps (more with the charge) of
  obsidian, crying obsidian, blackstone, basalt, tuff, deepslate and a little magma; each block has an 8% chance
  (`WRECK_ORE_CHANCE`) to be ore instead (iron ore, deepslate iron ore, raw iron, gilded blackstone). They're thrown out over the crater; they tumble, lie 5-11 s each and crumble (no drops). Lingering
  debris rides out explosions, so the crater rings don't take it. Less fire: the blasts no longer light the ground,
  the impact sets alight only 4 blocks around (was 10), fewer flames and lava drops. The symbiote now rides in one of
  the lumps and comes out (or is left as goo) when it breaks up; `WebSymbioteBlob.METEOR_CHANCE` is 10% (was 20%).

## 0.8.40: The Well around you, B to launch, the meteor through roofs, the shoulder view
- **Gravity Well (G)** is no longer aimed: it bursts out around you (the same bubble and risen lumps, from where you
  stand) and stays on you for 4 s, radius 8 (`WELL_RADIUS`, the bubble's size). It no longer pulls. Everything that
  touches it is weighed down (the heavy aura's weight, jump cut and Slowness, see `weighDown`) and kneels.
- **B is now tap or hold.** A tap (let go within 6 ticks, `CRUSH_TAP_TICKS`) is a force kneel around you: a
  quick Crush centred on you, radius 5 (`CRUSH_TAP_RADIUS`), pinning and kneeling everything in it. Tapped while
  your Well is up, it also launches you straight up about 14 blocks (`WELL_LAUNCH_HEIGHT`, worked out from your own
  gravity so Collapse's weight doesn't shorten it), once per Well. Falls still hurt. Held, it's the charged Crush
  where you aim, as before; with Collapsing Horizon the held release rains the orbiting ring down on the target
  (`GravityOrbit.rain`). The old G+B Cave-in is gone.
- **Meteor (C)** tears through every breakable block it passes on the way down (`GravityMeteor.carve`: 0.8 of its
  width, at most 7 blocks from its middle, 600 blocks a tick, 10% drop as items), so a roof it falls through is
  gone before the impact blast.
- **Shoulder view**: since 0.10.52 Shoulder Surfing Reloaded gives everyone the over-the-shoulder F5 view (our own
  Web/Gravity/Z Fighter one is gone; `client/CameraPreset` sets its offsets and keeps the camera coupled so abilities
  fire where the crosshair points).

## 0.10.11: Sideways falls, falling forward, wall walk
Ideas from Gravity Control (MIT; a NeoForge 1.21.1 fork of Gravity API), remade here: that mod turns the actual
"down" of an entity, camera and hitbox and all. We fake each effect with velocity and a turned body instead.
- **Sideways fall** (`GravitySideFall`): sneak as you let go of R, and what Lift held falls the way you look; **Sneak+G**
  (instead of the Well, same cooldown) drops everything within 8 outward, away from you. It falls flat along the
  ground with no weight, gathering 0.1 blocks/tick² up to 2.4, for up to 3 s. When it hits something it takes the
  fall at once (1.2 per block past the first 2.5, at most 24, as fall damage, so it counts as a death by weight),
  and the wall throws up its dust. Collapse's let-go plummet gives way to it.
- **Fall forward** (`GravityForward`, client `GravityForwardClient`): **Sneak+V while Weightless**. For 1.2 s "down"
  is where you look: you gather 0.18 blocks/tick² that way (steer by looking; air drag bends the old speed away).
  Creatures you hit take up to 7 hearts and are thrown along. Hitting a wall (or diving into the ground) at speed
  throws up debris and shakes your view; Weightless keeps you from the damage. Each uses 5 s of Weightless's 30 s;
  1.5 s between them. Everyone sees a head-first dive (`GravityWalkPose`).
- **Wall walk** (`GravityWalk`, client `GravityWalkClient`): once Gravity is fully mastered (or ascended, or
  corrupted). Sneak while facing a wall you're pressed against, or with your head against a ceiling: you stand on
  it. W goes the way you look along the surface (look up a wall and W climbs it), A/D go the way they look on
  screen, sprint is faster. Jump pushes off; Sneak again lets go and you fall. Walk off the top of a wall and you
  step up onto it; off any other edge, you fall. It drains a little hunger; the fall doesn't count while you're on.
  **The view stays upright**: only your body turns (feet on the wall, upside down on a ceiling) for everyone else.
  No wrapping round corners: walk off an edge and you drop.

- `/power set @s gravity`
- `/powerdebug gravity ready` clears every cooldown.
- 0.8.25: aim R at a player 50 blocks off (corners show); G on grass (lumps rise, take one with R); B a zombie
  (it kneels, gets up after 3 s); hold V (rides while held at `/power level @s 3`, toggles at 4); climb to the top;
  step off and hold V on the dropped board; call a meteor (`/power ascend @s`) and watch the wreck.
- `/powerdebug gravity kills 29`, then `/power level @s 4`, then make a lifted mob die from its fall.
- `/power corrupt @s`, then `/powerdebug gravity levitate 12`.
- Weight: with an empty hand, sneak + right-click a zombie, cow, iron golem or ghast (overhead, full walking speed);
  try the Ender Dragon (nothing). Fill a Kitchen supply cart and take it by the shafts: it comes, with no slowdown.
  Armor weight: `/give @s iron_chestplate[attribute_modifiers=[{type:"movement_speed",amount:-0.5,operation:"add_multiplied_total",id:"test",slot:"chest"}]]`,
  wear it: no slower after a second; `/power clear @s` and it slows you.

## 0.10.23: The earthboard's ride, refined
- The board is drawn right under its rider's feet every frame (it used to trail by the server's lag, so it slid out
  from under them at speed), and turns with the rider's heading since boarding.
- It banks with the rider's carve and tips with the flight: nose up climbing (10 degrees flat out), down sinking, and
  down a touch at top speed. The rider tips with it, crouches deeper climbing and rises a little sinking.
- Every lean and tilt is a damped spring (`GravityBoardPose.SPRING`, `DAMPING`), like Wind's hover sway: a sharp turn
  swings past and settles instead of snapping, and the forward pitch at speed eases in instead of jittering.
- Board and rider bob together slowly (`BOB`), less at speed. Stepping off, the board drops square again.

## Notes and risks
- Lifting a *player* sends them velocity updates every tick, so it may look jittery on their screen.
- The anti-kick mixin lets any player whose gravity is below 0.02 float, so a lifted player isn't kicked.
- Touches are kept in server memory for 10 s, so a server restart forgets them.
