---
name: wind-hover-sway
description: Wind hover sway (camera-following springs), aimable Tempest Kick, R-only take-off; merged PR #98 as pack 0.8.114
metadata:
  type: project
  modified: 2026-10-13T06:23:53.207Z
---
2026-10-13: Andy asked for a subtler, more realistic Wind hover with sways from camera movement. Merged PR #98 as pack 0.8.114 (2026-10-13 06:5xZ): WindHoverSway is all damped springs (lag camera yaw, lean into turns, look-pitch tip, climb sag, random gusts, lagging limbs); first person gets ~1° view sway + small bob via CameraMixin. Tuning constants at top of WindHoverSway.
Same PR: Tempest Kick (X tap) crescent follows look pitch (air: any; ground: level or up), re-aimed each wind-up tick. Sneak+double Jump take-off removed (R only) so the double tap belongs to the crawl. Not playtested.
