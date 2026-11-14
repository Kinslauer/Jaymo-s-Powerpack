# lasso: a 3D coil in the hand and a physics rope like the whip's
- Andy: "the chain axe and lasso should operate similar to the [whip] and flail, therefore they need blockbench models".
- **In the hand** the lasso is a 3D coil of rope (`lasso_coil`). A short tail hangs off it with the loop (a 3D model,
  `lasso_loop`) swinging on its end.
- **The rope** is a line of points on physics now (`client/forms/LassoRope`: gravity, drag, fixed links, resting on
  blocks), from the top of the coil as it is really drawn (`LassoSockets` and four `LassoSocket*` mixins, the way
  Powers finds a flail's handle) instead of a straight sagging line from a guessed spot under your eyes.
- **Thrown**, the loop is the 3D loop flying on the end of the rope (it was the flat inventory icon with no rope);
  **holding** something, the rope ends in a noose round its middle.
- Dropped or in a frame the lasso is the coil and loop in 3D (`lasso_whole`); the inventory icon is unchanged. The
  lasso's rules (reach, hauling, wrapping, breaking out) are untouched.
- Blockbench project `kingdoms/art/lasso.bbmodel`, written by `tools/gen_chain_weapons.py`.
- Compiled; not played. To look at in game: where the coil sits in the fist, and that the rope meets it in first and
  third person.
