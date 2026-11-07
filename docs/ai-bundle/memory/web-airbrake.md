---
name: web-airbrake
description: Powers 0.8.45 (PR #38 merged 2026-10-12): web wings Sneak airbrake, no glide landing damage, swing momentum ramp, falling spent zip lines
metadata:
  type: project
  modified: 2026-10-12T06:40:51.884Z
---
Andy asked 2026-10-12 (web wings): airbrake with arms waving forward (pictured), no fall damage landing from a glide, keep the
high-speed landing roll. Then: swinging starts slow and builds to the old max; spent Web Zip lines linger, fall and fade.
PR #38 merged 2026-10-12 on Andy's "Merge pr" as Powers 0.8.45 (master was still 0.8.40 then; #32/#33/#35/#37 claimed 0.8.41-0.8.44).
- Airbrake key = Sneak while gliding (picked as free; crouch pose suppressed while winged). `WebNet.WingsBrake`, `WebPower.WINGS_BRAKE`.
- Glide landing: `WINGS_FEATHER_FALLING` = 1.0 (no damage). Roll above `WebTrickClient.WINGS_ROLL_SPEED` unchanged.
- Momentum: `WebSwing.SWING_START_SPEED` 0.8, `SWING_RAMP` 0.011/tick up to `MAX_SPEED` 2.6; resets on ground.
- Spent zip lines: client-only `WebSpentLines` (HANG_TICKS 40, LIFE 80).
Built, not playtested. Jar: /mnt/project-files/web-airbrake/.

Update 2026-10-13, PR #78 merged as pack 0.8.93: airbrake moved to **S** (Sneak still works), bleeds speed over ~3 s
(`BRAKE_DRAG` 0.94) then a head-first freedive with wings swept back (`DIVE_*`). Swing momentum slower (start 0.55,
ramp 0.003 only near cap). Jump held on a web = no fall damage; releasing it springs you up by speed. Zip while gliding
is a 1.5 s boost (`WebPower.GLIDE_ZIP_TICKS` 30), then lets go with the kick up and the line falls limp: Andy's exact ask.

Update 2026-10-13, PR #85 merged as pack 0.8.100: Web Whip (Sneak+R/G, no web out) whips a mob/corpse/torn-out chunk
3 random ways and slams it (craters by size, chunk = creeper crater); every let-go web line falls limp (`WebSpentLines`).

Update 2026-10-13, PR #86 merged as pack 0.8.101: Andy WANTS a fast S-brake to throw you up (a no-lift fix was rejected).
Flare eases in (`BRAKE_EASE_IN` 12), lost speed × `BRAKE_LIFT` 1.4 becomes lift. Wings drawn as a faint cobweb that
ripples and billows/inflates on brake (`WebLineRenderer.drawWing`).
