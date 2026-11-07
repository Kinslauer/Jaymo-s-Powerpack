---
name: physics-mod
description: Physics Mod can't drive our corpses (closed, ARR, client-only, no API); own ragdolls built instead, PR #161 (pack 0.10.47 / Kitchen 0.4.39)
metadata:
  type: project
  modified: 2026-10-14T15:35:47.885Z
---
2026-10-14 Andy asked to work Physics Mod (Haubna) into our ragdolls. Not possible: Fabric 26.3 build exists but it's closed source, All Rights Reserved, client-only, no API. Andy picked "Build ours".
PR #161 MERGED 2026-10-14 15:5xZ (branch claude/project-thread-na40gt, pack 0.10.47 / Kitchen 0.4.39): Kitchen client/corpse/Flop = per-corpse client sim (spring limbs flung by death/landing/blows/shoves/walkers, ground slaps; whole-body blend into each new pose; fresh bodies fall from the keeled death pose via synced BORN; slope tilt; landing bob). Replaced Jolt. Powers SeveredPart rolls, rocks to rest on a spring, gets kicked by walkers. Built, not playtested. Jars: /mnt/project-files/ragdolls/.
Related: [[grabbable-severed-parts]], [[golem-corpses]].
