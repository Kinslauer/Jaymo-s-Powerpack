# Wither model spec (REA method)

Andy (2026-11-18): "yes send it to prometheus" (the Wither's new look, the one pick left from the Fire and Wither list: "A new look and sound (wr_look): our own Wither model, animations and sounds in the pack's style, like the boss remodels ... previews to Andy first").

## 1. References studied

|| Ref | What | Used for ||
|| --- | --- | --- ||
|| R1 | Vanilla Wither (Minecraft 26.3 / WitherBoss): 0.9 x 3.5 blocks, three skulls on a shoulder bar, no arms or legs | overall shape, head count and proportions, total height ||
|| R2 | Pack's existing boss remodels (Skeleton Lord, Grizzly) | style: vanilla-style cuboids, texture density ||
|| R3 | Pack's style: vanilla-style cuboids, 1 texel = 1/16 block (docs/Vanilla-Style.md) | box sizes, texture density, shade ramps ||
|| R4 | Kingdoms tools: `bbmodel_preview.py`, `bbmodel_bake.py`, `bbmodel_geckolib.py` | pose rendering pipeline, GeckoLib conversion ||

## 2. Facts (what the model must show)

|| # | Fact | Source | In the model ||
|| --- | --- | --- | --- | --- ||
|| F1 | Three skulls: one big centre skull, two smaller side skulls | R1 | three head boxes on a horizontal shoulder bar, centre skull higher than the sides ||
|| F2 | Shoulder bar across the top of the body | R1 | one long horizontal bar the skulls sit on ||
|| F3 | Core body beneath the bar | R1 | central body box under the bar ||
|| F4 | A spine hanging down from the body | task ("the real shape") | spine box from the body's centre down toward the tail ||
|| F5 | Ribs on the spine | task | pairs of rib boxes on the spine at three heights ||
|| F6 | A tail at the bottom | task | tail box below the spine, ending the silhouette ||
|| F7 | No arms, no legs | R1 | no arm or leg boxes anywhere ||
|| F8 | Model faces north (-z) in Blockbench, feet at y 0 | R2/R3 | front faces along -z, pivot at feet ||
|| F9 | 128×128 texture sheet (one texture, pack style) | R3 | resolution: {width: 128, height: 128} ||
|| F10 | Each head can rotate independently | task spec | separate head groups with individual rotations ||
|| F11 | Total height about 3.5 blocks like vanilla | R1 | tail tip to centre skull top is 56 model px ||
|| F12 | Wither core glows violet in the dark (0x6A3FA0, the pack's wither colour) | kingdoms WitherFight.VIOLET | violet-painted areas on the texture for the glow layer ||

## 3. UNKNOWN / guesses (decided here, change freely)

- U1: box sizes. Chosen: body core 20×14×10 px, shoulder bar 28×6×8 px, centre skull 12×12×12 px, side skulls 8×8×8 px, spine 6×12×2 px, ribs 4×1×1 px pairs, tail 4×12×4 px. All at 1 texel = 1 px.
- U2: proportions. Chosen: centre skull top at y 44, tail tip at y -12, so the silhouette is 56 px (3.5 blocks) tall, the same as vanilla.
- U3: skull facing. Chosen: all three skulls look north (-z) in rest pose; side skulls angle slightly inward in the idle animation.
- U4: glow. Chosen: the texture has a violet area on each skull's front and on the body core's front; the glow layer draws it bright in the dark (decided at integration, not painted full-bright).
- U5: idle animation. Chosen: a slow hover bob (root y +/-1 px) with the centre skull looking side to side (+/-15 deg) and the side skulls following gently.
- U6: scale at integration. Chosen: 1.0 at model level; the renderer's scale argument sizes it in game (the pack's boss remodels use ~1.35).

## 4. Animations wanted (keyframes to write once Andy approves the look)

Named for `kingdoms/.../wither/WitherMoves.java` and `WitherFight.java`: `idle` (hover, heads looking), the three heads looking on their own (`adjustModelBonesForRender` head look, not an animation), `ravage` (lobbing skulls at buildings), `orbit` (circling the quarry, faster in the last third), `beam_tell`, `beam_fire`, `charge_tell`, `charge_rush`, `stun` (after a blocked charge), `climb` (rising for the skull rain), `throw_blues` (blue skulls), `spawn` and `death`. Moves the server starts play on an `action` controller through `triggerAnim`.

## 5. Differences left (validation)

- Check renders: front view, side view (left and right), three-quarter rear view, plus posed renders (skulls looking left/right, charge crouch).
- No missing faces or texture gaps at cube connections; touching faces between cubes omitted where they'd z-fight (body top/bottom, bar top/bottom, spine ends, skull bottoms).
- Central skull tallest; side skulls slightly lower, as in vanilla.
- Violet glow areas clearly distinct on the texture.
- Box sizes at 1 texel = 1 px: an 8 px skull is half a block.
- After Andy approves: GeckoLib conversion via `tools/bbmodel_geckolib.py` — geo.json, animation.json, stacked texture PNG all parse; then renderer, sounds and the `action` controller, then the PR.
- Say plainly in the PR that it hasn't been seen in game.