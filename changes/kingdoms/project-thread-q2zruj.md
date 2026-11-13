# the Aether Giants animated from Blockbench, and the snow giant's own body
Andy: "is it possible to animate our giants with blockbench and geckolib?" and, about the Snow Giant, "add it to the
giants being worked on, as its own upright model."
- **Aether Giants** (`remnants/AetherGiant`, `client/remnants/GiantRenderer`). The four Giants are drawn by GeckoLib now.
  The body is the same boxes on the same skins, written as a Blockbench project (`kingdoms/art/aether_giant.bbmodel`,
  by `kingdoms/tools/gen_giant_geo.py`), and its moves are keyframes there, made from the numbers the old model had in
  code: `idle`, `ready` (fists half raised), `blow_right`, `blow_left`, `stamp`, `grab`, `grab_recover`, `death_right`,
  `death_left`. Open the project in Blockbench, change a move, export with `tools/bbmodel_geckolib.py` (the script's
  header says how) and the game plays it. The server starts each blow, stamp, grab and the fall.
- Still in code, because they follow the game each frame: the stride (the legs land on the server's steps), the head
  turning where it looks, the flinch, and the break-up after the fall. `GiantModel` and `GiantState` are gone.
- Changes you may see: the fall is one of two (right arm out or left) instead of mixed at random per Giant, and an arm
  that is striking or grabbing no longer twists with the stride.
- **Snow giant** (`wild/SnowGiant`). Its own upright body in place of the white grizzly at twice the size: a shaggy
  white giant 3.4 blocks tall with a mantle, a bare slate face, two short horns, a beard of icicles, a jaw that opens,
  elbows and knees (`kingdoms/art/snow_giant.bbmodel`, by `kingdoms/tools/gen_snow_giant.py`; spec in
  `docs/Snow-Giant-Model-Spec.md`). Its animations are named for the moves it already had and last as long (rear and
  slam, swipes, charge, maul, roar, stagger, plus idle, walk, run and death), so nothing about how it fights changed.
  The old white grizzly skin is removed.
- Compiled; not played. Unseen in game: every Giant move under GeckoLib (most of all the break-up and that each blow
  starts on time), and the snow giant's size against its hitbox and its moves against their hits.
