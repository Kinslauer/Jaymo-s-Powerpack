# Magic FX: every effect has a type
- Andy: "Every effect should have a type, (beam, blast, miasma, etc) and go to the same asset for each one, like the
  beacon for beams". New `core/fx/MagicFx` with `client/fx/MagicFxClient`: **beam** (the beacon beam: core and glow),
  **blast** (a swelling shell round a white-hot heart), **ring** (a shockwave over the ground), **orb**, **sigil** (a
  turning circle of marks on the ground), and two particles, **mote** (a glint of light) and **miasma** (a soft dark
  puff). Only colour, size and time vary. Dark colours draw as murk in every type.
- Every recoloured redstone dust in the powers, alchemy, baubles and Aether guardians is now a mote, or miasma if its
  colour is dark (Darktide's ink, gravity's shadow, flux, blight, gas). 22 one-off dust clouds (transformations,
  surges, wave impacts, self-destruct) and 27 recoloured firework flashes are blasts. The god ritual's pad beams are
  real beams; the full-ki burst and milestones throw a ring.
- Left alone because they aren't magic: the symbiote's goo, Hive, redstone charging the Rig, attack tells, leaves, gun
  muzzle flashes, tournament fireworks.
- Rules for new effects and what is still to move: `docs/MagicFx.md`. Textures: `tools/gen_magic_fx.py`.
- Compiles (Gradle, `compileJava compileClientJava`); not played. Lines and circles drawn as rows of dust inside loops
  are rows of motes for now, and END_ROD / ELECTRIC_SPARK style particles are a later pass.
