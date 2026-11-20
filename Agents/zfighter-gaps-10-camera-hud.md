---
status: in-review
task: zfighter-gaps-10-camera-hud
branch: claude/zfighter-gaps-10-camera-hud
with:
pr: 1937
mods:
  - powers
versions:
started: 2026-11-20
owns:
  - src/client/java/com/jaymospowers/client/FrontCamera.java
  - src/client/java/com/jaymospowers/client/CameraPreset.java
  - src/client/java/com/jaymospowers/client/power/zfighter/ZFighterClientConfig.java
  - src/client/java/com/jaymospowers/client/power/zfighter/ZFighterHudLayout.java
  - src/client/java/com/jaymospowers/client/power/zfighter/ZFighterKiSenseClient.java
  - src/client/java/com/jaymospowers/client/power/zfighter/ZFighterFlightClient.java
  - src/main/java/com/jaymospowers/power/zfighter/ZFighterSensesNet.java
  - src/main/java/com/jaymospowers/power/zfighter/ZFighterKiSense.java
  - src/main/resources/assets/jaymospowers/lang/parts/camera.json
  - src/main/resources/assets/jaymospowers/lang/parts/zfighter_senses.json
---
# Z Fighter gaps 10: camera and HUD

**Task:** Implement front camera settings (distance, height, side offset, smoothing), HUD options (hide numbers, always-visible values, menu scale, markers over party members), damage tally on ki sense (show how much harm a sensed foe has taken), and travel/combat flight modes (fast travel vs nimble combat flight with a lock).

## Plan
- Add front camera settings to ZFighterClientConfig and use them in FrontCamera.java
- Add HUD options to ZFighterClientConfig and ZFighterHudLayout
- Extend ki sense to show damage tally (need server-side changes to send damage info)
- Add travel/combat flight modes to ZFighterFlightClient with a toggle

## Notes
- Need to check ZFighterKiSense on server side to understand what data is available
- The task mentions "No new original mobs" and "No slash commands for player features"
- Abilities.md rows for every new input

## Done
- Created worktree and agent note
- Read relevant source files
- Implemented front camera settings with smoothing
- Implemented HUD options (hide numbers, always visible, menu scale, party markers)
- Implemented ki sense damage tally (server and client)
- Implemented travel/combat flight modes with lock
- Added translations for all new settings
- Created change note
- Built successfully with `./gradlew build`
- PR checks passed
- Opened PR #1937
- Set task to in-review