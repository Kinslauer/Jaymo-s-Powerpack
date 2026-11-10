# Stegonaut asset credit

Stegonaut's geometry, 256×256 UV layout, hides, marking sheets and motion are **Jaymo's Kingdoms project art**: the body and textures from `kingdoms/tools/gen_stegonaut.py` (as the former Java `StegonautDragonModel` used them), the motion from that model's own pose equations. No external model, texture or animation data is included.

The editable Blockbench model and its GeckoLib keyframes are written by `kingdoms/tools/gen_stegonaut_geo.py` (Prometheus, the modelling bot). Change the numbers there and run it again rather than editing the `.bbmodel` by hand.

Saint's Dragons by Leon Saint is a **gameplay reference only** (its code is MIT; its art is All Rights Reserved). No Saint's Dragons art, models, textures, sounds or animation data were used.

## Asset notes

- `stegonaut.bbmodel`: 55 uniquely named bones, 55 cubes, 18 animations.
- States: idle with three grumbles (`idle_toss`, `idle_tilt`, `idle_raise`; `idle` is the plain loop), `walk`, `run` (the gallop), `sit` (with the doze), `crash`, `swim`, `hurt`, `death`. Stegonaut never flies: no fly, glide or land.
- Moves the server starts: `chin_slam`, `ground_slam`, `chew` (the ground eating), `roar`, `bite`, `graze`, and `sweep` (new; the old model had no pose for its tail sweep).
- Hide and marking selection, horn variants, broken plates, saddle/girth/pad visibility, the 1.3 draw scale, the 0.35 hatchling factor, seats and culling reach are unchanged.
- `stegonaut-render.png`: the rest pose, five views (`tools/bbmodel_preview.py`; the tack shows because the preview draws every bone).
- `stegonaut-comparisons.png`: each pose drawn by the old model's equations (`POSE=` in `tools/bbmodel_preview.py`, top) over the new keyframes (bottom). A move's render leaves out the idle sway under it, which the game blends in from the `move` controller.
- GeckoLib (Tslat and contributors, MIT) draws it at runtime; none of its code or art is in these files.
