# gore pieces in Blockbench, tatters cut from the body's own clothes
Andy: "i might have you go back and make these parts in blockbench", then "the tatters should use the colors on the
corpse' model that it came from".
- **Blockbench models**: the six insides thrown by a body blown apart or eaten (organ, guts, ribs, spine, bone, tatters)
  are now one Blockbench file, `tools/bbmodels/gore-pieces.bbmodel` (six groups side by side, one 64x64 texture): a
  lobed heart with its vessels, a coiled rope of gut, an arched rack of four pairs of ribs on a breastbone, five
  crooked vertebrae with their spurs, a long bone snapped at one end with the marrow showing, a ragged scrap of five
  strips. Baked by `tools/bbmodel_bake.py` to `bbmodels/gore.json` and drawn through a Kitchen copy of the `BbModel`
  loader (`client/bb`). Edit the file in Blockbench and re-bake (the command is in `kitchen/tools/gen_gore.py`); organ
  and gut are painted grey because the game tints them with the body's blood.
- **Tatters**: a scrap is cut from the body it came off (`GibRenderer.tatters`): an MCA villager's own clothing texture
  (or the one MCA draws that player in), else the player's skin, from where clothes lie (trunk, arms, legs); any other
  creature gives a face of one box of its own model (a humanoid's trunk or limbs, never its head). Only a body the
  client can't rebuild leaves the plain cloth swatch. An outfit from MCA's online library falls back to the skin.
- Compiled; not played.
