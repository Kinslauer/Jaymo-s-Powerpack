# Remove double-tap Sneak zoom gesture

The double-tap Sneak (within 6 ticks) to activate OK Zoomer's zoom is removed. The Shift+Left Alt zoom gesture remains unchanged.

## What the player will see
- Double-tapping Shift no longer zooms the camera.
- Holding Shift + Left Alt together still zooms until Alt is released.
- Alt alone still rolls (dodges).
- The slide gesture (holding Sneak for 1.5s in a sprint) is unaffected.

## Changes
- `SneakZoom.java`: Removed `DOUBLE_TAP_TICKS` constant and `sinceLastTap` field. Removed the double-tap detection logic in `tick()`. The Shift+Alt zoom path and cling/bar/slide blocking logic remain.
- `Slide.java`: Updated javadoc to remove reference to "zoom's double-tap".
- `FreerunClient.java`: Updated javadocs in `trySlide()` and `slideWindup()` to remove references to the zoom double-tap.
- `docs/AI-HANDBOOK.md`: Updated Controls section to remove "tap then hold Shift" from the Zoom description.

## Not tested in game
Zoom behavior with OK Zoomer mod not verified in multiplayer.
