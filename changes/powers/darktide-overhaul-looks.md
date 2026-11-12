# Darktide overhaul, part 2: looks
Andy's picks from the Darktide picklist, the looks half. Three new Magic FX types, for any power to use, and Darktide
redrawn with them.
- **New effect types** (`docs/MagicFx.md`): **wave** (a wall of water that rises, rolls, curls and breaks), **stroke** (one
  ink brush mark, heavy at the start and dry at the end, for any cut) and **pool** (a flat sheet with ripples).
- **Black Tide is a real wave**: dark water 2.5 blocks high (4 at spring tide) with a pale crest, rolling with the hit
  itself, breaking at the end and leaving the ground marked. Ascended, pools lie along its path.
- **Every cut is an ink stroke**: Dark Strike, Crescent (the whole circle at spring tide), Ink Cross, the riposte, Fang
  Barrage, Pounce, the flurry, Riptide Dash and Stillwater's cuts. The vanilla sweep and the dotted arcs are gone.
- **Undertow**: a dark pool where you sink and where you'll rise, a shadow over the ground between them, spray as you burst out.
- **Stillwater** is a mirror of still black water with slow ripples instead of a dotted circle.
- **The tide shows on you**: at high tide water lies at your feet and your eyes shed a pale light; at spring tide the
  water is wider, ink rises off your shoulders and your blade paints a stroke behind each swing.
- **Maelstrom**: waves roll in from every side as the dome rises and out as it comes down; the ground under it is water.
- **No vanilla stand-ins left**: squid ink is miasma, rain splash is pale spray off a ring, a parry is a pale ring (with
  steel sparks when a blade turned it), the shade and the risen shed miasma. Gifted villagers' Darktide kit uses the
  same stroke, pool and wave.
- Classes: `DarktideLooks` (new: colours, `cut`, `stab`, `spray`, `ink`, `pool`), `MagicFx` and `MagicFxClient` (the three
  types; the `Shape` packet carries a stroke's bend), `DarktideFx` (mirror, tide on the body), every Darktide move class.
  Textures from `tools/gen_magic_fx.py`: `textures/fx/wave.png`, `stroke.png`, `pool.png`, `ripple.png`.
- Compiled (main and client); not played. The eyes' light is drawn as pale glints at the eyes, not a change to the skin.
