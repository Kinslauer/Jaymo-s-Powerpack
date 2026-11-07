---
name: smoke-vapour-flight
description: Barrel rolls for all flights + Smoke Vapour Form as smoke-legged flight on R + thicker smoke; MERGED PR #183 as pack 0.10.81
metadata:
  type: project
  modified: 2026-10-14T21:52:15.452Z
---
2026-10-14 21:24Z Andy: double-tap A/D while flying (Wind, Xeno, Z Fighter, Smoke) = barrel roll; Vapour Form turns everything below the waist to smoke as a Wind-like flight, moved G->R (Smokescreen to G, Vapour is the starter); smoke "way thicker".
MERGED 2026-10-14 21:51Z: PR #183 (pack 0.10.81; 0.10.79/0.10.80 still held by open #178/#182; next free 0.10.82). Jar: /mnt/project-files/smoke-flight/. core/FlightRoll + client/FlightRolls (roll drawn in WindPose rotate/tilt, camera roll in ScreenShakeMixin). SmokeFlightClient/SmokeFlightPose (legs hidden via Dismembered.hidePart). Old Vapour invisibility/"mobs lose you" removed. Not playtested.
Related: [[smoke-power]].
