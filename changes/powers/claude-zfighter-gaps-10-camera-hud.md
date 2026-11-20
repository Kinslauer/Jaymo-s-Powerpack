# Z Fighter gaps 10: camera and HUD

## Front Camera Settings
- The F7 front camera (client/FrontCamera.java) now has configurable settings:
  - **Distance**: How far the camera sits from the player (default 3.4 blocks)
  - **Height**: Vertical offset of the camera (default 0.1 blocks)
  - **Side Offset**: Horizontal offset of the camera (default -0.9 blocks)
  - **Smoothing**: How smoothly the camera moves to its target (default 0.15)
- Settings are stored in `config/jaymospowers-client.properties` and synced to Shoulder Surfing's config at runtime.
- Changed `CameraPreset` marker to v3 to re-apply the preset with the new defaults.

## HUD Options
Added four new HUD options to `ZFighterClientConfig`:
- **Hide Numbers**: Hides numeric values on the Z Fighter HUD (ki, combo count, etc.)
- **Always Show HUD**: Shows the Z Fighter HUD even when not playing as a Z Fighter
- **HUD Menu Scale**: Scale multiplier for the HUD editor and menus
- **Party Member Markers**: Shows markers above party members when ki sense is active
- Accessible via `ZFighterHudLayout.isHudHideNumbers()`, `isHudAlwaysVisible()`, `getHudMenuScale()`, `isHudPartyMarkers()`

## Ki Sense Damage Tally
- When enabled, ki sense now shows how much damage a sensed entity has taken.
- Server-side (`ZFighterKiSense`): Calculates damage taken as `maxHealth - currentHealth` and sends it in the `Sense` packet.
- Client-side (`ZFighterKiSenseClient`): Stores damage taken and max health for each sensed entity.
- New methods: `getDamageTaken(Entity)`, `getMaxHealth(Entity)`, `isDamageTallyEnabled()`
- The `Mark` record in `ZFighterSensesNet` now includes `damageTaken` and `maxHealth` fields.

## Travel and Combat Flight
- Added two flight modes toggled via client settings:
  - **Travel Mode**: Faster cruise speed (2x), wider turn radius (0.5x turn rate)
  - **Combat Mode**: Normal cruise speed (1x), tighter turn radius (1.5x turn rate)
- **Lock Flight Mode**: Prevents accidental switching between modes.
- Settings: `ZFighterClientConfig.getFlightTravelMode()`, `getFlightModeLocked()`
- Client-side getters in `ZFighterFlightClient`: `isTravelMode()`, `setTravelMode()`, `isModeLocked()`, `setModeLocked()`

## Files Changed
- `src/client/java/com/jaymospowers/client/FrontCamera.java` - Added smoothing and config sync
- `src/client/java/com/jaymospowers/client/CameraPreset.java` - Added runtime config sync, bumped marker to v3
- `src/client/java/com/jaymospowers/client/power/zfighter/ZFighterClientConfig.java` - Added all new settings
- `src/client/java/com/jaymospowers/client/power/zfighter/ZFighterHudLayout.java` - Added HUD option getters
- `src/client/java/com/jaymospowers/client/power/zfighter/ZFighterKiSenseClient.java` - Added damage tally support
- `src/client/java/com/jaymospowers/client/power/zfighter/ZFighterFlightClient.java` - Added travel/combat flight modes
- `src/main/java/com/jaymospowers/power/zfighter/ZFighterKiSense.java` - Server-side damage calculation
- `src/main/java/com/jaymospowers/power/zfighter/ZFighterSensesNet.java` - Extended Mark record and Sense CODEC
- `src/main/resources/assets/jaymospowers/lang/parts/camera.json` - Front camera config translations
- `src/main/resources/assets/jaymospowers/lang/parts/zfighter_senses.json` - HUD, ki sense, and flight config translations

## Not Tested In Game
- Front camera smoothing with Shoulder Surfing
- HUD options (hide numbers, always visible, menu scale, party markers)
- Ki sense damage tally display
- Travel/combat flight mode switching
- All settings persistence across restarts