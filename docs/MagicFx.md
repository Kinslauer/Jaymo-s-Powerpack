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
| Spark | `MagicFx.spark(color, size)` (a particle) | A short jagged fork that jumps shape every tick, full bright | Lightning, crackle, charge, static |
| Rune | `MagicFx.rune(color, size)` (a particle) | One of six marks from the sigil's alphabet; drifts, or is drawn in to its point when sent with a direction | Enchanting, wards, knowledge, written magic |

- `MagicFx.wisp(color, size)` picks mote or miasma by how dark the colour is (light can't be black). Use it when the
  colour is only known at run time.
- `MagicFx.burst(level, x, y, z, color, size, count, dx, dy, dz)` is what a one-off cloud of dust was: a blast that
  size with motes.
- Stock looks, for where a vanilla particle stood in for magic: `MagicFx.LIGHT` (was END_ROD), `CRACKLE`
  (ELECTRIC_SPARK), `GLYPH` (ENCHANT), `VOID` (PORTAL, REVERSE_PORTAL), `HEX` (WITCH), `GLOW`, `EMBER` (WAX_ON), `PALE`
  (WAX_OFF), `MURK` (DRAGON_BREATH). Kingdoms has the same names on `Fx` (no `MURK`). Use them wherever you'd pass a
  particle.
- `MagicFx.line(level, from, to, particle, points)` and `MagicFx.circle(level, particle, at, radius, points)` are for
  helpers that used to dot a line or a circle with one particle: a mote, spark or rune becomes a thin beam or a ring in
  its colour; anything else (miasma, vanilla) is still dotted.
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

## Second pass (2026-11-11)

- **Spark and rune** added. Vanilla particles standing in for magic were swapped for the stock looks above: about 540
  uses in 202 Powers files and 130 in 61 Kingdoms files (END_ROD, ELECTRIC_SPARK, ENCHANT, PORTAL, REVERSE_PORTAL,
  WITCH, GLOW, WAX_ON, WAX_OFF, DRAGON_BREATH, and tinted ENTITY_EFFECT, which is now a wisp).
- **Dotted lines and circles to beams and rings**: the shared helpers (`XenoForm.beam/ring`, `Rivet.line`,
  `ZFighterGift.line`, `ArsenalGift.streak`), the Sun patron's column (`PatronWeaponHits`) and the Sun Marshal's
  landing beam (`SunMarshalMoves`).

## Not moved

- `kingdoms/.../client/films/LoreFilmClient.java` (8 dusts): the film thread owns it (PR #1284).
- Loops that dot their own line or circle without a shared helper (Z Fighter ring arenas, ki rain, Chrono's clock,
  gravity wave, spirit arms, champion tell rings in `MoveKit`, `PatronWrath`, `HeritageKits`, the Aetherion dragon).
  They use the typed particles; each is a per-ability change to a beam or ring.
- Attuned-only mana lines through `Mana.sendSeen`: only some players may see them, and shapes go to everyone in range.
- Vanilla particles that are the real thing, not a stand-in: electric sparks on machines, rigs, sentries and android
  malfunctions, portal specks at real portals and raids, enchant glyphs at enchanting storage, wax on jewellery, glow
  on beasts and squid, parry and clash sparks (`MobParry`, `BladeClash`, `AttackTells`, the clash thread's), horses,
  MCA villagers' everyday marks, souls, crits, totems, sonic booms.
- The symbiote's goo specks (`power/web`), Hive, Rubber-folk and Bio-built body specks: not magic, left as they are.
