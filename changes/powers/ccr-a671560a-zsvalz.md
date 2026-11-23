# Dodge: a tap dashes, a hold rolls; adrenaline runs on wind; no climb cooldown
- **Dodge key (Left Alt) on the ground**: a tap is now a short dash that keeps you on your feet (about 2-3 blocks,
  the way you hold, with the dodge's usual evasion window and feather cost), so a heavy red-glint blow can be slipped
  and answered straight away. Hold the key and the dash goes on into the combat roll; let go before the roll ends to
  come up standing, or keep holding to go down into prone as before. Others see the dash upright and the roll tumble.
- The landing roll, the flip, the backflip, boxing's evades and the dives are unchanged.
- Classes: `stamina/Dodge` (`DASH_SPEED`, `DASH_UP`, `DASH_TICKS`; `Start.dash`, `Rolling.dash`), `client/stamina/DodgeClient`,
  `client/stamina/DodgeRollPose`.
- **The dash's pose**, seen by everyone: the feet plant and the hips drop (wide and straight-legged for a side dash, a
  lunge with the front shin upright for a forward or back one), the upper body leans into the way, both arms come up
  to a guard (left alone while you swing or use an item, so a counter shows), and the head stays level, on the foe. In
  first person the view dips a little and leans into a side dash. Rubber-folk and Verdants dash with their stretch.
- Classes for the pose: `client/stamina/DodgeRollPose` (`DASH_*` knobs, `stance`).
- Built; not played. The pose was checked frame by frame in a scratch run of `tools/pose_preview.py`, not in game.
- **Adrenaline** kicks in at most once every 15 minutes (kept through relogging and death). As it kicks in it fills
  Jaymo's Kitchen's wind bar, the wind burns while it lasts (about 30 s standing, 11 sprinting, nothing comes back),
  and when the wind runs dry the adrenaline is gone (a rise from the downed begun on it still finishes). Hits while it
  runs no longer start it again. Without the wind (Kitchen's stamina rule off, or an older Kitchen) it lasts 10 s as
  before. `core/wounds/Adrenaline` (`COOLDOWN`, `READY`, `WIND_LONGEST`).
- **Climbing**: no wait after pulling yourself up over an edge; the next edge can be caught at once (letting go still
  waits 10 ticks so you don't catch the edge you dropped from). `client/LedgeClient`.
