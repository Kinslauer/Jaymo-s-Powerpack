---
status: in-review
for: any
from: coordinator
asked: 2026-11-07
thread: Claude project thread "Check open work against Hermes fixes"
claimed_by: hermes-a
pr: 1124
suggested_owns:
  - src/client/java/com/jaymospowers/client/blood/
  - src/client/java/com/jaymospowers/client/wet/
---
# Blood and overlay textures: pop-in and z-fighting

**Andy's words (2026-11-07 17:45):** "Id also like another look over the blood and overlaid textures for pop in and
zfighting"

**What's wanted:** go over every flat thing we draw on top of something else and stop it flickering (z-fighting) or
appearing/vanishing suddenly at distance or on spawn (pop-in):
- Ground decals: `BloodDecals`, `BloodPuddle` (also used for rain/drip water puddles, `BloodDecals.Decal`), stains on
  slopes, slabs, stairs, snow layers, carpets and at block edges; fade in/out instead of popping; draw distance and
  culling consistent with nearby particles.
- Overlays on bodies/items: `BloodCoatLayer` (blood on creatures), `BloodItemOverlay` (blood on swords/axes),
  `ArmourGrimeMixin` (grime tint), wound/cut overlays (`core/wounds`), Kingdoms Armory 3D pieces and `WornModel`
  where they sit over armour, Kitchen corpse overlays. Check render type (decal/translucent, depth offset / polygon
  offset, small outward inflate) and that they follow EMF/Fresh Animations poses.
- Test with and without Complementary shaders and Fresh Animations where possible; note what couldn't be checked.
- Report the list of fixes in the PR. Tuning (offsets, fade times, distances) as `static final` constants.

**Where to start:** `docs/codemap/powers.md` "com.jaymospowers.client.blood", `client/wet/`, memories on EMF (PR #185,
#223 WornModel/EmfWornPose). Kingdoms/Kitchen overlay files only if found guilty; add them to owns first.

## Notes
- 2026-11-07 13:31 Review (head 3fe29ec): changes needed. Good: found a real cause (the puddle lift shrank to ~0 as it faded, BloodPuddle.java:384), culls puddles by their whole quad instead of the centre (:255), wall marks and weapon blood lifted, constants, MODLOG, 0.21.55, honest 'not tested' line. To do: 1) The ground lift is now big enough to see: DECAL_LIFT 0.06 plus up to 0.03 jitter puts puddles and footprints 1 to 1.5 pixels above the floor, so they visibly float, most of all where a puddle hangs over a block edge or lies on a slab, carpet or snow layer. Keep the lift small (a few thousandths of a block, scaled up gently with camera distance if needed) and stop the flicker with the render state instead: a polygon/depth offset or the decal-style render type the particle uses, so it stays flush. 2) Most of the task's list wasn't looked at: fading decals in and out instead of popping on spawn and at draw distance; stains on slopes, slabs, stairs, snow layers, carpets and block edges; BloodCoatLayer, ArmourGrimeMixin, the core/wounds overlays, Kingdoms Armory 3D pieces/WornModel over armour and Kitchen corpse overlays (render type, offset, inflate, and following EMF/Fresh Animations poses). Go through each one and put the list in the PR body: what was wrong and fixed, or checked and fine, file:line, and what couldn't be checked (shaders, FA) with the reason. Fix the ones that are guilty (add Kingdoms/Kitchen files to owns first). Then build, pr_checks, push, set status=in-review.
- 2026-11-07 14:11 Review (head 0b278a0): changes needed (second review). Fixed: the ground lift is small again (DECAL_LIFT 0.002), and the PR body now lists the audit (wall marks, weapon blood, BloodCoatLayer, ArmourGrimeMixin, wounds, Kitchen corpses). To do: 1) The flicker isn't fixed, only moved further away: the PR says '26.3's RenderType/RenderSetup have no polygon or depth offset', but 26.3 has depth bias in DepthStencilState (biasScale, biasConstant): RenderPipelines.java:1041-1047, LINES_DEPTH_BIAS = new DepthStencilState(CompareOp.GREATER_THAN_OR_EQUAL, true, 1.0F, 1.0F). With only a 0.002-block lift and no bias, ground decals z-fight again past a few dozen blocks as depth precision drops, which is Andy's complaint. Give the decal group (BloodDecals.TYPE) its own pipeline, copied from the translucent particle one with a depth bias, and draw puddles, footprints and wall marks through it; keep the lift tiny. 2) Kingdoms wasn't audited: the PR says Kingdoms Armory 3D pieces/WornModel are 'in a separate checkout, not inspectable', but kingdoms/ is in this same repo (kingdoms/src/client/java/.../WornModel, ArmorPoseLivingEntityRendererMixin, EmfWornPose). Check them like the rest and list the result. 3) Pop-in at draw distance isn't covered: the task asks decals to fade in and out instead of popping on spawn and at the edge of view distance; say where the puddles' and wall marks' draw distance is (constant, file:line) and fade them over the last few blocks instead of a hard cut, or show it already does. Then build, pr_checks, push, set status=in-review.
- 2026-11-07 17:18 hermes-a's last run stopped before finishing; handed back as changes so it can carry on.
- 2026-11-07 17:51 Review (head be8b269): changes needed (third review; last round's three items are fixed, these are new). Fixed: decals draw through their own pipeline with a depth bias (BloodDecals.DECAL_PIPELINE, same bias as vanilla's LINES_DEPTH_BIAS), the lift stays tiny, puddles and wall marks fade at the edge of view distance, and the audit covers Kingdoms' WornModel/EmfWornPose. To do: 1) Far-away puddles fade out for good: BloodPuddle.java:298 multiplies the particle's own alpha field (this.alpha) every frame while it's near the view-distance edge, and this.alpha is only recalculated in the final lifetime fade (:285), so a puddle seen from far drops toward 0 a little more each frame and stays faded when you walk back to it. Compute the distance fade into a local variable used only for this frame's colour (as BloodWallMark does with its local 'alpha'), and leave this.alpha alone. 2) No checks ran: the PR conflicts with master in gradle.properties (master is 0.21.54 now). Merge origin/master, keep 0.21.55 (still free), keep master's MODLOG and append only this entry. 3) BloodWallMark.java:36-37: the new DRAW_DISTANCE_FADE javadoc and constant start at column 0; indent with a tab like the lines around them. Then build, pr_checks, push, wait for green checks, set status=in-review.
