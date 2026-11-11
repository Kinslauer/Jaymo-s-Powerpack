# Magic FX: every effect has a type

Andy (2026-11-11): "Every effect should have a type, (beam, blast, miasma, etc) and go to the same asset for each one,
like the beacon for beams, and they can vary in size intensity etc but that keeps it consistent and more like a magical
system than a bunch of bs particles."

Powers: `core/fx/MagicFx` (server and common), `client/fx/MagicFxClient` (the drawing). Kingdoms: `Fx` (same calls,
looked up at run time, old dust if Powers is missing). Textures: `tools/gen_magic_fx.py`.

| Type | Call | Drawn as | Use it for |
|---|---|---|---|
| Beam | `MagicFx.beam(level, from, to, radius, color, ticks)` | The beacon beam: bright core in a wider glow, a small orb at each end | Any line of power between two points |
| Blast | `MagicFx.blast(level, at, radius, color)` | A shell swelling out and thinning, a white-hot heart, motes thrown out | A burst at a point, a flash |
| Ring | `MagicFx.ring(level, at, radius, color)` | A band running out over the ground with a low wall of light on its front | Shockwaves, pulses |
| Orb | `MagicFx.orb(level, at, radius, color, ticks)` | A ball, see-through at the rim, round a bright heart | Power held in place (send again to keep it) |
| Sigil | `MagicFx.sigil(level, at, radius, color, ticks)` | Our circle of marks, turning on the ground | A working laid on the ground |
| Mote | `MagicFx.mote(color, size)` (a particle) | A four-point glint, full bright | Loose power in the air; where a redstone dust was |
| Miasma | `MagicFx.miasma(color, size)` (a particle) | A soft puff lit by the world | Dark or heavy power, gas, smoke, blight |

- `MagicFx.wisp(color, size)` picks mote or miasma by how dark the colour is (light can't be black). Use it when the
  colour is only known at run time.
- `MagicFx.burst(level, x, y, z, color, size, count, dx, dy, dz)` is what a one-off cloud of dust was: a blast that
  size with motes.
- On the client alone (films, looks drawn from synced state): `MagicFxClient.beam/blast/ring/orb/sigil`, and
  `level.addParticle(MagicFx.wisp(...), ...)`.
- Sizes of motes and miasma are on redstone dust's scale, so old numbers carried over.
- Dark colours draw as murk in every type: no white core, a deeper heart.

## Rules for new effects

1. Pick a type from the table. Never `DustParticleOptions` or a recoloured `ParticleTypes.FLASH` for magic or ki.
2. Vary colour, size and time only. If none of the types fits, add a type here (one asset, one renderer), don't
   improvise with vanilla particles.
3. Not magic, so not this system: steel sparks (`ClashFx`), blood, redstone itself, goo, gunpowder flashes, fireworks
   at a win, leaves, footstep dust.

## What moved (2026-11-11)

- **Beams**: Kingdoms' spell beams (`Sorcery.beam`, `Restoration.beam`, Necromancy, Hemomancy's dark bolt, the scroll
  bolt in `Arcana`), town golems' rays, the Grue's grasp, the god ritual's pad beams. Z Fighter waves were already
  beacon beams (`ZFighterBeams`) and are unchanged.
- **Blasts**: 22 one-off dust clouds in Powers (transformations, surges, wave impacts, self-destruct, the gravity
  training field, fairies) and 7 in Kingdoms (patron blessings, turning, in-balming, soul flares, the Grue), plus 27
  recoloured firework flashes in Powers and 2 in Kingdoms.
- **Rings**: Restoration's rings, the full-ki burst, milestones.
- **Sigil**: the Sanctuary circle.
- **Motes and miasma**: every other recoloured dust in the powers, alchemy, baubles, spirits, heritage looks and
  champions (about 230 places). Dark colours became miasma by themselves; gas, smoke, flux and blight were set to it.

## Not moved yet

- `kingdoms/.../client/films/LoreFilmClient.java` (8 dusts): the film thread owns it (PR #1284).
- Lines and rings still drawn as rows of motes inside loops (Z Fighter ring arenas, ki rain, Chrono's clock, gravity
  wave, spirit arms, attuned-only mana lines through `Mana.sendSeen`). They now use the mote asset; turning each into a
  real beam or ring is a per-ability pass.
- Other vanilla particles used as magic (END_ROD, ELECTRIC_SPARK, ENCHANT, REVERSE_PORTAL, WITCH, ENTITY_EFFECT:
  about 800 uses). Same approach, next pass.
- The symbiote's goo specks (`power/web`), Hive, Rubber-folk and Bio-built body specks: not magic, left as they are.
