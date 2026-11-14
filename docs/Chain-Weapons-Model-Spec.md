# Chain axe and lasso models

Andy (2026-11-14): "the chain axe and lasso should operate similar to the [whip] and flail, therefore they need
blockbench models". Both are our own designs: every box and pixel is written by `tools/gen_chain_weapons.py`, nothing
is taken from another mod or model.

## How they are split
Like the flail and the whip, only the part in the fist is the held item. The line is physics drawn by the game, and
the thing on its end is a model put there each frame.

| | in the fist | the line | on the end | dropped / framed |
|---|---|---|---|---|
| Chain Axe (Powers) | `chain_axe_handle` | `LashRenderer`'s iron chain | `chain_axe_swung` | `chain_axe_whole` |
| Lasso (Kingdoms) | `lasso_coil` | `LassoRope`'s rope | `lasso_loop` | `lasso_whole` |

Inventory slots keep the painted 16 px icons.

## Facts
- A chain weapon of this kind (haft, chain, weighted head) is swung in circles to build speed and released: the
  flail's whirl and slam. Source: the flail already in the pack (`armory/Lash`).
- A bearded axe has a blade that drops below the eye in a hook, which is what lets it catch and pull: the chain axe's
  tug. Source: general knowledge of the type; UNKNOWN whether any real chained axe was made this way, it is a fantasy
  weapon here.
- A lasso is a coil held in one hand with the rope running to a loop closed by a small eye (the honda). Source:
  general knowledge.

## Chain axe (32 px texture `item/chain_axe_model.png`)
- Haft: 2 x 10 x 2 wood, iron butt cap and collar, leather wrap over the lower half, a staple on top at (8, 14.5, 8).
  That point is `LashSockets.CHAIN_AXE_TIP`; move both together.
- Head: hangs from the top middle of its box (8, 16, 8) by a ring; an eye 3 wide, a spike behind it, a cheek, a blade
  with a beard 3 px below the eye and a bright ground edge. Drawn at `LashRenderer.AXE_HEAD_SCALE` (0.8).
- Palette: wood 120/86/52 and 84/58/36, wrap 70/44/28, iron 150/152/158 with 198 highlights and 92 shadows, blade
  96 to 176 grey toward the edge, edge 226/228/230. The icon's colours.

## Lasso (32 px texture `item/lasso_model.png`)
- Coil: three square hoops of 1 px rope side by side, a leather keeper round the bottom turn (where it is gripped), a
  short lead out of the top at (8, 11.5, 8.1): `LassoSockets.COIL_TIP`.
- Loop: one hoop 10 x 11.5, a 2 px honda knot at the top middle (8, 16, 8). Drawn at `LassoRope.LOOP_SCALE` (0.9).
- Palette: hemp 196/165/106 with a 140/112/66 lay, knot 164/134/82, keeper leather 92/60/36.

## Differences that remain
- Java item models only turn a box about one axis in 22.5 degree steps, so the hoops are square, not round.
- The axe head does not turn its edge into the swing; it hangs as the chain's last link points.
- The noose round a held creature is the shackle rope's flat ring, not the loop model stretched to fit.
- None of this has been seen in game yet: sizes and the grip positions are from the numbers, not from a screenshot.
