# Gravity

Taking the weight out of things, or piling it on. All server-side in `power/gravity/GravityPower.java`. A target's
weight is its `minecraft:gravity` attribute, changed with **transient** modifiers, so nothing sticks if the server stops
mid-ability. The HUD is in `client/power/gravity/GravityPowerClient.java`: chips bottom-right, in Wind's style.

## Abilities
**Unlock order:** R, V, G, B. Crush is charged, so it comes last.

| Key | Ability | What it does |
|---|---|---|
| R (hold) | Lift | The creature under the crosshair, within 24 blocks, loses its weight and floats where you point. You hold it at the distance you grabbed it from, clamped to 3–14 blocks. Let go and its weight returns, so it falls, and fall damage is real. It works on players too. Max 6 s, 8 s cooldown. Lifted creatures go limp, reusing Web's ragdoll. |
| V | Weightless | Toggle. Your gravity drops by 70% and you take 50% less fall damage. It drains hunger and lasts up to 30 s, then a 10 s cooldown. Water or an empty hunger bar ends it. |
| G | Gravity Well | A point where you aim, up to 24 blocks, pulls everything within 7 blocks (mobs, players, items) for 4 s. 14 s cooldown. |
| B (hold, release) | Crush | Charges over 3 s. Everything around the aim point is slammed down. Radius scales 2.5→5.5 and damage 4→16 with charge, plus Slowness II, and targets weigh 4× as much for 3 s. Cooldown is 6–20 s, depending on the charge. |

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

## Testing
- `/power set @s gravity`
- `/powerdebug gravity ready` clears every cooldown.
- `/powerdebug gravity kills 29`, then `/power level @s 4`, then make a lifted mob die from its fall.
- `/power corrupt @s`, then `/powerdebug gravity levitate 12`.

## Notes and risks
- Lifting a *player* sends them velocity updates every tick, so it may look jittery on their screen.
- The anti-kick mixin lets any player whose gravity is below 0.02 float, so a lifted player isn't kicked.
- Touches are kept in server memory for 10 s, so a server restart forgets them.
