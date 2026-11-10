# The Omen (red and blue)

- **Models and skins:** "The Omen Red" and "The Omen Blue", Blockbench Workshop, CC BY 4.0 (`the_omen-LICENSE.txt`).
  The sources are `tools/bbmodels/the-omen-red.bbmodel` and `the-omen-blue.bbmodel`; the Workshop pages' author isn't
  recorded in the repo (it wasn't when the models first came in, PR #678). Add their name here and in
  `textures/entity/bb/CREDITS.txt` if it turns up.
- **Changes (ours):** animations added by `tools/gen_omen_geo.py` (idle, walk, run, turn_left, turn_right, smash,
  fire, hurt, death_red, death_blue; the Workshop's own `pose_1` kept, and each death ends in that build's `pose_1`),
  written into `the_omen_red.bbmodel` / `the_omen_blue.bbmodel` (editable in Blockbench) and exported for GeckoLib 5
  with `tools/bbmodel_geckolib.py`. Geometry and skins unchanged.
- **Renders:** `the_omen_<build>-<pose>-comparison.png` (old code pose above, new animation below, from
  `tools/omen_compare.py`), `the_omen-animations.png` (idle, run, turn_left, hurt).
