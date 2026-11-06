---
name: wind-flight-swing
description: Wind flight swings timed to Better Combat attacks (PR #138 merged, pack 0.10.8); how BC/PAL are read
metadata:
  type: project
  modified: 2026-10-14T08:51:36.891Z
---
2026-10-14: Andy said Wind's flight animations break when swinging and wanted a flight-specific swing that goes with Better Combat. PR #138 MERGED 2026-10-14 05:16Z (pack 0.10.8; master jumped 0.10.5 to 0.10.8, so open PRs #134-#137 on 0.10.6/0.10.7 must re-bump to 0.10.9+): WindFlightSwing gives cruising flyers their own slash/chop/thrust/barrel-roll spin, timed to the attack cooldown. Hovering lets BC's own swing through, and PAL's first-person pass skips all wind body poses. Not playtested.

**Hard-won:** BC plays attacks on PAL layer `bettercombat:attack`, read via `PlayerAnimationAccess.getPlayerAnimationLayer` (PAL compileOnly in build.gradle, only touched in wind/PalAccess). BC is ARR, so don't compile against it. PAL applies bones at PlayerModel.setupAnim TAIL, so our ModelFeatureRenderer prepareModel inject wins. Related: [[combat-animations]], [[wind-hover-sway]].

2026-10-14 08:2xZ: same thread, Wind air brake 3x particles (6 clouds/tick own view, 5/tick for others, gusts twice as often) and ~2x brake time (AIR_BRAKE_KEEP 0.93, 19 ticks top speed to hover). PR #148 MERGED as pack 0.10.24.

2026-10-14: same thread. The Wind Cyclone now takes on an element from the blocks, sky and creatures around it, or from a Kingdoms Sorcerer's imbue, which counts the most. It becomes a Fire Tornado, Waterspout, Blizzard, Sandstorm, Storm Cyclone or Gale Cyclone (WindCycloneElement). The client draws the funnel like a Kingdoms tornado, but white (WindCycloneFunnel). PR #149 MERGED as pack 0.10.26.
