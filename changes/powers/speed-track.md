# Speedster track: gears, momentum, healing, rapid hands, held phase, rings, slow time, marked run
Overclock (V) and Velox's Velocity (R) share one speedster core; everything below works for both unless it says otherwise.
Ideas only from another mod's speedster (All Rights Reserved): every line of code here is our own.
- **Speed gears**: scroll the mouse wheel while sprinting at speed to shift between 5 gears (the hotbar keeps the wheel at
  a walk). Gear 3 is the old speed; higher gears are faster, turn wider, burn more hunger and leave a longer streak. The
  gear shows on a chip bottom right above the power's own block (`ChronoGears`, `ChronoGearsClient`, `ChronoSpeedHud`, `ChronoScrollMixin`).
- **Momentum, skids, drift turns**: speed builds over the first strides and holds on a straight line; a sharp turn sheds
  speed (more in a high gear) and leans the body into it; letting go of every key slides you to a stop in a spray of the
  ground's dust (`ChronoMomentumClient`, lean drawn by `ChronoRunPose`).
- **Speed healing**: gears 3 to 5 close wounds as healing magic does (through `Mending`, so limb wounds are respected) for
  far more hunger than rest.
- **Running blow**: a melee hit at a sprint deals up to triple damage by speed and gear and shoves the target along your path (`ChronoRapid`, `ChronoRunBlowMixin`).
- **Rapid bows**: full draw after 4 ticks; crossbows load in a tick (`ChronoRapidBowMixin`, `ChronoRapidCrossbowMixin`).
- **Catch and hurl**: at speed, use while aiming at a shot in flight within 6 blocks plucks it from the air and holds it
  before you (a gold bar shows the hold); use again to throw it back at 2.2 times its speed.
- **Weapon snatch**: sprinting past an armed mob and pressing use takes its main-hand weapon (into your hand, your pack, or the ground). Never players.
- **Held phase (Overclock only)**: hold V with a wall ahead to vibrate through blocks for up to 5 s (`ChronoPhase`,
  `ChronoHeldPhaseMixin`). Floors hold, unbreakable blocks hold; let go inside a block and you are put out at the nearest
  clear spot; running dry inside one costs 2 hearts; a cooldown follows. Replaces the old blink.
- **Running rings**: running a tight closed circle (radius 2 to 6, round in about 2.5 s) raises a vortex that lifts the creatures inside, spins them and puts out fire (`ChronoRings`).
- **Slow time**: hold `.` at speed and the world drops to a fifth of its pace while you and other players move normally,
  for as long as a meter lasts (`ChronoSlowTime`, vanilla's tick freeze with a stepped tick; no real slowed tick rate, which would slow the runner too).
- **Marked run**: Sneak + `/` marks the place you stand (a column of light shows it); `/` later runs you there by yourself
  along a path around obstacles, within 128 blocks on loaded chunks; any movement key cancels (`ChronoSpeedKeys`, `ChronoMarkRun`).
- New keys (rebindable): slow time (`.`), mark / run to the mark (`/`).
- Built and compiled; not played. To check in game: the scroll mixin and gear shifts, the bow and crossbow mixins, walking through walls without rubber-banding (and the way out), the lean direction on turns, slow time's stepped tick, and the marked run's path on stairs and ledges.
