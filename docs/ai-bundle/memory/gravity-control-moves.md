---
name: gravity-control-moves
description: Gravity 0.10.6 (PR #135): sideways fall, fall forward, wall walk ideas from Gravity Control; key choices made on Andy's behalf
metadata:
  type: project
---
2026-10-14 Andy asked what Gravity could take from Gravity Control (MIT, NeoForge 1.21.1 fork of Gravity API) and picked 3 of my 5 ideas: wall walk, sideways fall, fall forward (they skipped Gravity Anchor field and gravity potions). PR #135 MERGED 2026-10-14 05:3xZ as pack 0.10.11, not playtested.

Key choices I made (Andy may change): sneak+release R and sneak+G = sideways fall; sneak+V while Weightless = fall forward (costs 5 s of Weightless); wall walk needs full mastery, sneak against wall/ceiling, camera stays upright (only body turns; I offered camera turning as a bigger follow-up).

**Why:** Andy may ask to rebind or to turn the camera. **How to apply:** code in power/gravity/GravitySideFall, GravityForward, GravityWalk (+ client GravityWalkClient/GravityWalkPose). See [[gravity-0825]].

Earthboard ride animation refined (board drawn under rider each frame, banks/tips, spring leans, shared bob): PR #147 MERGED 2026-10-14 08:2xZ as pack 0.10.23, not playtested. Tuning in GravityBoardPose constants.
