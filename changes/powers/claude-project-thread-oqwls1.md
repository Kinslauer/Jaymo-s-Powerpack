# Rig leg kick: taekwondo side kick and combo roundhouse
- **Side kick** (V with a Rig leg, no suit): the knee chambers up and folds, the hips turn side-on, and the heel flicks
  out level with the hip, the body leaning away over the standing leg. 9 damage (Siege leg x1.4), a hard throw, a beat
  of hit-stop, and it breaks the block it lands on plus the four round it on that face (hardness up to 3: stone, ores,
  wooden doors; never where you may not break blocks). Cooldown 12 ticks (was 40). On the ground it steps in, in the air
  it still lunges.
- **Roundhouse in a combo**: V within 1.5 s of landing a melee blow spins a full turn and whips the shin out at the end:
  1.8x the side kick's damage at everything round you, a bigger throw, a 3x3 face of blocks up to hardness 5. The heel
  leaves a glowing trail in the leg's frame colour and the leg steams through the turn; your camera swings all the way
  round with you (first person goes to third person for the spin and back).
- **Hop** moved to Sneak + V on the ground (40-tick cooldown as before).
- `RigLimbs`, `RigMoves.ROUNDHOUSE`, `RigPose` (kick frames, whole-body turn, knee bends), new `RigKickCamera` +
  `RigKickCameraMixin`. Compiled; not played.
