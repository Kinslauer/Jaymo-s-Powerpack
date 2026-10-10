# Carrying (0.8.0, everyone)

Anyone, with any power or none, can pick up a player or a small animal and carry them. Server: `core/Carry`.
Client: `client/CarryClient` (following, sway, the struggle key) and `client/CarryPose` (both bodies' poses, the
first-person arms and the carried player's camera).

## Controls
| Who | Input | What happens |
|---|---|---|
| Anyone | **Sneak + right-click** a player or a small animal | Pick it up (empty hand or not). One at a time; not while carrying a Kitchen corpse. |
| Carrier | **Right-click** (anywhere, even at the air) | Put it down from the arms, with your momentum. |
| Carrier | **Sneak + right-click** | Throw it a short way ahead. |
| Carried player | **Jump** | Break free (one jump), unless stunned or otherwise unable to act. |

These are the same controls as Jaymo's Kitchen's corpses. Small animals: the `jaymospowers:carryable` entity tag
(wolf, cat, ocelot, fox, chicken, rabbit, parrot, frog, armadillo), plus any baby animal (`Animal.isBaby()`).
Animals never break free.

## Gravity users (0.8.1)
Gravity ignores weight, at every mastery level (`power/gravity/GravityWeight`):
- With an **empty main hand**, sneak + right-click picks up **any living mob** (`GravityWeight.liftable`), not just
  players and small animals. Never: the Ender Dragon, the Wither, anything in the `c:bosses` entity tag, armor stands,
  anything wider than `MAX_WIDTH` 4 or taller than `MAX_HEIGHT` 6 blocks (a Giant), or something held by Lift. (The
  empty hand keeps sneak-feeding, shearing, leads and the like working on mobs; players and the small animals still
  work with anything in hand, as for everyone.)
- **No carry slowdown**, whatever they hold (players too): the `carry_weight` modifier is never put on.
- A mob (never a player) wider than `OVERHEAD_WIDTH` 0.8 or taller than `OVERHEAD_HEIGHT` 1.0 goes up **overhead**
  (hold 4, see below) instead of a rolled hold: a zombie, a cow, a spider, an iron golem, a ghast.
- If they stop being a Gravity user mid-carry, a mob only they could carry is dropped.
- A big mob is let go ahead of them by half its width; a throw from overhead is a **heave**: slower and lower
  (`HEAVE_AHEAD` 0.38, `HEAVE_UP` 0.18, from 1.4 blocks up) with a heavy thud.

**Any carried mob's AI is on hold** (any carrier, `power/gravity/mixin/GravityCarriedMobMixin` on
`Mob.serverAiStep`): no goals, brain, pathing, targeting or attacks; a creeper stops swelling. It comes back the
moment it's let go. A carried body also never suffocates in a wall (`isInWall` is false), since it really stands at the
carrier's feet while it's drawn up in the hold.

## Holds
Each pickup rolls a hold (synced in the carrier's `jaymospowers:carry_carrying` attachment):

| # | Hold | Rolled | Carried body | Carrier's arms |
|---|---|---|---|---|
| 0 | Fireman's carry | on foot, in flight | hips on the right shoulder, folded over it: head and arms down the back, legs in front | right arm round the legs |
| 1 | Bridal carry | on foot, in flight | across both arms in front, face up, head to the left; inner arm round the neck | both forearms out under the body |
| 2 | Shoulder grab | flight only | hung by the left shoulder at your right side, legs dangling | right arm down at the side |
| 3 | Over the back | flight only | back to back, facing up behind you (lying on you in a cruise) | both arms reaching back |
| 4 | Overhead | Gravity, big mobs only (never rolled; kept through take-off and landing) | up on your raised hands, its underside at 2.15, bobbing and slowly drifting round; a tall thin body (zombie, enderman) lies across, face up, head to the left; limbs float slowly | both arms straight up, spread round a wide one |

- **Wind flight** (see `docs/wind.md`): take-off rolls a new hold from all four; landing with a flight-only hold
  switches to 0 or 1. Flight is ×0.8 top speed and ×1.3 hunger while carrying.
- Hold positions are in world blocks whatever the body's scale (a baby's model scale is undone around the hold).
- **Small animals** get a simpler cradle per hold, set by their size: draped over the shoulder, cradled half on their
  back in both arms, dangling by the scruff, riding on your back.
- **Loose limbs:** the carried body's arms and legs hang and drift, each on its own slow beat, and swing like damped
  pendulums against the carrier's motion (`CarryClient.SWAY_*`): they trail behind when you go, swing on when you
  stop, swing out of turns, and jog with each step. In flight they trail and flutter in the wind (`TRAIL_FULL_SPEED`).
  The whole held body swings a little with them (`CarryPose.BODY_SWAY`).

## How it works
- **No vanilla riding.** The carried body is put at the carrier's feet every tick and drawn in the hold from there:
  - Server: animals are moved there each tick (`Carry.tickCarry`), their pathing stopped.
  - Every client moves each carried body to its carrier's feet at the end of each tick, from the carrier as that
    client sees them, so the two move as one on every screen.
  - A carried **player's own client** moves itself the same way (player movement is client-authoritative) and tells
    the server through its normal movement packets. `mixin/CarryPacketListenerMixin` stops the server from kicking
    them for floating or moving too fast, while carried and for `RELEASE_GRACE_TICKS` 40 after.
  - The two never shove each other (`CarryLivingEntityMixin.doPush`), and the carried body isn't pickable (no
    crosshair or projectile hits it, so the carrier's own arrows and clicks go past it).
- **Rendering** goes through the usual pose path, as the last word: `CarryLivingEntityRendererMixin` /
  `CarryAvatarRendererMixin` (priority 2000) put `HELD_KEY` / `CARRIER_KEY` / `THROWN_KEY` on the render state and
  clear the fall and web ragdoll poses on a held body; `setupRotations` moves it into the hold (tipped with a cruising
  carrier via `WindPose.tiltLike`); `CarryModelFeatureRendererMixin` poses the limbs after `setupAnim`.
- **First person:** the carrier's hands show the hold (`CarryFirstPersonMixin` draws the bare arms instead of the
  held items); the carried player's camera sits at their head in the hold (`CarryCameraMixin`).
- **Letting go** (`Carry.end`): the body is put where it was held (`RELEASE_AT` per hold; at the carrier's feet if
  there's no room) with a velocity, through a teleport for players (position and motion in one packet, after a
  `carry_self(-1)` so the client stops following first). A throw also sets `jaymospowers:carry_thrown`: the body
  turns over and its limbs flop for `THROWN_TICKS` 20 (1 s). Fall damage applies normally on landing.
- **Escaping:** a carried player's jump sends `carry_struggle`. `Carry.canStruggle(player)` says no while: Gravity's
  held stun (`GravityPower.HELD`), held by Lift (`LIFTED`), on a web (`WebPower.HELD` / `HITCH`), still ragdolling from
  a throw, webbed down (jump strength < 0.05), or asleep. **There's a marked TODO in `canStruggle` for Chrono's new
  hit-stun.** A failed struggle shows "You can't move!" and can't be retried for 0.5 s.
- **It ends** on: either death, logout, a dimension change, the carrier being teleported (> 8 blocks in a tick) or
  the two drifting more than `LEASH` 4 blocks apart (plus 6 blocks per block/tick of the carrier's speed, for lag),
  sleeping, or either one riding something. The same body can't be picked up again for 1 s.
- **While carried** a player can't attack, use items or blocks, or break blocks (both sides). Their own Wind flight
  stops. The carrier walks 15% slower (5% with an animal): the `jaymospowers:carry_weight` modifier.

## Packets and attachments
- C2S `carry_release(toss)`, `carry_struggle`; S2C `carry_self(carrierId)`.
- Attachments (all synced, none saved): `carry_carrying` (carrier: target id + hold), `carry_carried_by` (carried:
  carrier id), `carry_thrown` (game time of the throw). Holder: `core.Carry` (in `registerAttachments()`).
- Lang: `message.jaymospowers.carry.*` in `lang/parts/core.json`.

## Tuning
`Carry`: `PLAYER_WEIGHT` 0.15, `ANIMAL_WEIGHT` 0.05, `FLIGHT_SPEED` 0.8, `FLIGHT_HUNGER` 1.3, `LEASH` 4,
`LEASH_PER_SPEED` 6, `TELEPORT_JUMP` 8, `THROWN_TICKS` 20, `REGRAB_TICKS` 20, `DROP_AHEAD` 0.12, `DROP_UP` 0.08,
`THROW_AHEAD` 0.5, `THROW_UP` 0.32, `ESCAPE_UP` 0.42, `ESCAPE_BACK` 0.3, `STRUGGLE_COOLDOWN` 10,
`OVERHEAD_RELEASE_PER_WIDTH` 0.5, `HEAVE_AHEAD` 0.38, `HEAVE_UP` 0.18.
`GravityWeight`: `MAX_WIDTH` 4, `MAX_HEIGHT` 6, `OVERHEAD_WIDTH` 0.8, `OVERHEAD_HEIGHT` 1.0.
`CarryPose`: each hold's `*_X/Y/Z`, `FIRE_TIP` 105°, `BRIDAL_TIP` 76°, `SHOULDER_LEAN` 10°, `BACK_TIP` 14°,
`BODY_SWAY` 7°, `LIMB_SWAY` 0.55, `THROWN_SPIN` 160°, `OVERHEAD_Y` 2.15, `OVERHEAD_BOB` 0.07, `OVERHEAD_DRIFT_*`,
`OVERHEAD_FLOAT` 0.22, `OVERHEAD_LIE_RATIO` 1.4, `OVERHEAD_ARMS` / `OVERHEAD_SPREAD`. `CarryClient`: `SWAY_*`, `TRAIL_FULL_SPEED` 1.2.

## Testing (two clients on a dedicated server)
1. Sneak + right-click the other player: they're picked up in a fireman's or bridal carry (pick up a few times to see
   both). Both screens show the same hold; the limbs swing as you walk, stop and turn. You walk a little slower.
2. As the carried player: you can't hit, mine, place or use items. Your first-person view is at your head in the hold.
   You aren't kicked for flying, however long it lasts.
3. Right-click: they drop in front of you. Sneak + right-click: they're thrown a few blocks, tumbling for a second,
   and take fall damage off a ledge.
4. Pick them up again; they press Jump and spring free. Grab them with Gravity's Lift first (held stun), then carry:
   their jump fails with "You can't move!".
5. As a Wind user: carry someone and take off. The hold re-rolls (all four appear over several take-offs); cruising,
   the body lies along the flight with you. Land with a shoulder grab or over-the-back hold: it switches.
6. Animals: wolf, cat, chicken, rabbit, parrot, frog, armadillo, a baby cow. They follow and never break free.
7. Ends: `/tp` the carrier far away, change dimension, log either one out, kill either one: the carry ends cleanly.
8. With a Kitchen corpse in your arms you can't pick anyone up, and while carrying someone you can't pick up corpses.
9. As a Gravity user (`/power set @s gravity`), empty hand: pick up a zombie, a cow, an iron golem, a ghast, a creeper
   about to blow. They go up overhead and float there; you walk at full speed; they don't attack, and the creeper
   stops hissing. Right-click sets one down ahead; sneak + right-click heaves it. They act again once down. The Ender
   Dragon, a Wither and a Giant can't be picked up. Carry another player: no slowdown, a normal hold.
10. As a non-Gravity player nothing changed: only players and small animals, and the slowdown.

## Risky at runtime
- Overhead: the hold is computed, not seen. Check a wide mob (ghast, iron golem) isn't inside the carrier's head, a
  lying zombie faces up, and nothing is culled when you look up at it (`CarryLivingEntityRendererMixin` widens the
  culling box of any held body). The carrier's raised-arm `zRot` sign may need flipping if the arms cross inward.
- A big mob stands (invisibly) at the carrier's feet; when put down with no room ahead it's put there, so it can end
  up in a wall in a tight corridor.
- The carried player's client positions itself; with lag, other screens still show them glued to the carrier (each
  client places them), but the server-side position can trail at speed. The leash allows for it.
- The hold geometry and the first-person arm offsets are computed, not seen: tune `CarryPose` constants in game.
- Injection order: the carry's render hooks run at priority 2000 so they come last; another mod's pose code that runs
  later could still move a carried body's limbs.
