# Weather and looks, part 2 - the Nether's air, shore foam, warmer dawn and dusk
- **The Nether's air**: ash drifts over the Nether Wastes (the one Nether land that had nothing on the air), and small embers rise off lava lakes anywhere in the Nether.
- **Shore foam**: flecks of white on water that lies against land. Only while no shader pack is drawing the world, since Complementary draws its own water; checked every 2 seconds, so it follows the shader toggle and low-end mode.
- **Dawn and dusk**: on a clear day the far haze warms toward amber around sunrise and sunset (colour only, the view distance is untouched).
- **Clouds building before rain** is already what storm cells do (their deck and towers gather over a minute before the rain), so nothing new was added for it.
- Not built: pixel shadows and water caustics with shaders off. Both need their own render passes, which can't be written blind from the cloud; with Complementary as the default they would only show for players in low-end mode.
- All in `client/disasters/SkiesClient` (tuning constants at the top) and `SkiesFogMixin`; `DarkNights.shaderPackOn()` is the shader check.
- Built (compiles); not seen in game.
