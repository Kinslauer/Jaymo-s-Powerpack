# TEST: ImmersiveVFX in place of Iris and Complementary
- A trial Andy asked for ("We can test this to see if it runs better, if not it gets scrapped"). **ImmersiveVFX 2.0.5**
  (all rights reserved, alpha, Fabric 26.3, client-only, shipped unchanged) joins the pack: pixel shadows, shore foam
  and caustics, clouds and sky, fire, ground fog, sand winds, auroras, heavier rain.
- Its page says it does not work with Iris, so **Iris and the Complementary Unbound default leave** while it is in
  (the build moves Iris out of the CurseForge profile), and **Sodium goes from 0.9.2 to 0.9.3-beta.4**, which it needs.
- No code changes. `ShaderPreset`, `LowEndMode` and Kingdoms' dark-night Iris mixins all check for Iris and do nothing
  without it; the Aether veil's Sodium mixin compiles against the beta.
- What to look at in game: frame rate against the old shaders, dark nights (they were dimmed through Iris), the Aether
  staying hidden from the ground, `/lowend`, and whether Bobby and the other Sodium mods still load on the beta.
- To scrap it, revert this PR: the comments in `build.gradle` say which lines.
- Compiled; not run. Nothing here has been started with ImmersiveVFX or the Sodium beta.
