---
paths:
  - "kingdoms/art/**"
  - "tools/bbmodels/**"
  - "tools/bbmodel_*.py"
  - "kingdoms/tools/gen_*_geo.py"
  - "tools/gen_*_gecko.py"
  - "**/geckolib/animations/**"
---
# Animating a Blockbench model with keyframes of our own

(Moved here from `docs/AI-HANDBOOK.md` section 3.)

How Notus and Boreas moved to GeckoLib (2026-11-09; the example is `kingdoms/tools/gen_fire_dragon_geo.py`). Use it
for a model that comes with too few animations, or to move a mob off our own loader (`BbModel`, `BbMobRenderer`, or
a model posed in Java) without changing how it looks.

1. **Write the animations in a script, not by hand.** The script reads the source `.bbmodel` (`tools/bbmodels/`), adds
   animations in Blockbench's own format (an `animators` entry per bone, keyed by the bone's group `uuid`, keyframes
   with `channel`, `data_points`, `time` and `interpolation`), and writes `kingdoms/art/<mob>.bbmodel`. Then it runs
   `tools/bbmodel_geckolib.py` on that file. Andy can still open the result in Blockbench, but the script is the
   source: change the numbers there and run it again.
2. **Units and signs.** In a "free" or "modded entity" project, keyframe values are the game's way: degrees as a
   `ModelPart`'s angles, positions with x and y turned round (+y is up). A bone's resting angle in the game is the
   Blockbench rotation with x and y negated. Keyframes are *added* to the resting pose, so to hold a part at an
   absolute angle `a`, key `a - rest`. Old Java posing code turns straight into keyframes this way: `part.xRot += d`
   becomes a key of `d`, `part.xRot = a` a key of `a - rest`, and `top.y -= 19` a position y of +19.
3. **Check every pose against the old one before writing Java.** Bake to scratch (`tools/bbmodel_bake.py <art> <scratch>
   <mob>`) and render with `tools/bbmodel_preview.py <scratch> <mob> out.png <anim> <seconds> <texture>`. The old
   code's pose renders with the `POSE` variable (`bone:=x:y:z` sets an angle, `bone=x:y:z` adds, `bone@=x:y:z` moves
   the pivot), so you can put the two side by side. Pass `--drop` to the bake too, for any bone the GeckoLib files leave out.
4. **Controllers.** A `move` controller picks the looping animation every frame from the entity's own client-side
   state (`AnimationTest.animatable()`), and `test.setControllerSpeed(...)` keeps a loop in time with the game (the
   dragons' wingbeat follows their stroke rate, the walk their walk speed). An `action` controller plays one-off moves
   the server triggers (`triggerAnim`), from hooks the base class already has (`WindDragon.bitten`/`swept`).
   Controllers aren't additive by default: a later controller replaces an earlier one on every bone it keys. So key
   only the bones a move needs (the bite keys the neck, head and jaw) and the legs keep walking underneath.
5. **Things that follow the game every frame** (head look, a jaw while breathing, pitching with a climb) go in the
   renderer's `adjustModelBonesForRender`. GeckoLib's x and y rotations are the negative of a `ModelPart`'s (see
   `DefaultAnimations.hardcodedHeadRotation`), so `part.xRot += d` becomes `bone.setRotX(bone.getRotX() - d)`.
6. **Textures that change per mob** (hides, markings): override the model's `getTextureResource(GeoRenderState)` and
   read a `DataTicket` the renderer adds in `addRenderData`. Glowing markings are a `TextureLayerGeoLayer` with
   `RenderTypes::eyes` (`FireDragonRenderer`).
7. Credit and licence beside the art (`<mob>-CREDITS.md`, `<mob>-LICENSE.txt`), a render (`<mob>-render.png`), and
   update `textures/entity/bb/CREDITS.txt` if the mob was listed there. Build. Say plainly that the animations
   haven't been seen in game.
