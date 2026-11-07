---
name: sneak-zoom-alt-dodge
description: Zoom = double-tap-and-hold Sneak driving OK Zoomer's key (SneakZoom); dodge = Left Alt only (PR #974 merged 2026-11-05, Powers 0.20.65)
metadata:
  type: project
---
Andy 2026-11-05: "double tap and hold shift should be default zoom, and alt should be roll". PR #974 (Powers 0.20.65, merged 2026-11-05 02:02Z): client/SneakZoom holds OK Zoomer's key.ok_zoomer.zoom down (setDown) while a double-tapped Sneak is held; DodgeClient lost its double-tap-Sneak dodge (Left Alt only); KeyConflicts v7 unbinds OK Zoomer's key (was on Left Alt via v3, clashing with dodge). AirFlip still waits out the double-tap window (now zoom's). Built, not played. Related [[double-tap-crawl]].
2026-11-05 07:1xZ: Andy "shift + left alt should make you zoom": PR #981 (merged 07:2xZ as Powers 0.20.74) holds zoom while Sneak + Dodge key are both down; Alt with Sneak held no longer dodges (DodgeClient.keyDown()).
2026-11-05 08:06Z: Andy said Shift+Alt did not zoom on 0.20.77 (double tap did). PR #990 merged as Powers 0.20.79: DodgeClient.keyDown() also reads Left Alt / bound key from SDL keyboard state (InputConstants.isKeyDown). Not verified in game; if still broken, suspect the Windows Alt+Shift language hotkey.
2026-11-05 12:28Z: Andy confirmed Shift+Alt zoom works after #990. PR #1017 merged as Powers 0.20.98: Shift+Alt zoom ends only when Alt is released (Shift release keeps it); double-tap zoom still ends with Shift.
