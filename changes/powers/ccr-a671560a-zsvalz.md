# Dodge: a tap dashes, a hold rolls
- **Dodge key (Left Alt) on the ground**: a tap is now a short dash that keeps you on your feet (about 2-3 blocks,
  the way you hold, with the dodge's usual evasion window and feather cost), so a heavy red-glint blow can be slipped
  and answered straight away. Hold the key and the dash goes on into the combat roll; let go before the roll ends to
  come up standing, or keep holding to go down into prone as before. Others see the dash upright and the roll tumble.
- The landing roll, the flip, the backflip, boxing's evades and the dives are unchanged.
- Classes: `stamina/Dodge` (`DASH_SPEED`, `DASH_UP`, `DASH_TICKS`; `Start.dash`, `Rolling.dash`), `client/stamina/DodgeClient`,
  `client/stamina/DodgeRollPose`.
- Built; not played. The dash has no pose of its own yet (the body just skims upright).
