---
name: captive-conduct
description: Kidnapped/shackled players spared by captors until they act up; visible collar+chain/rope tether, break with rocks/saw; halving bleeds out in 8 s (PR #1009 merged)
metadata:
  type: project
  modified: 2026-11-05T11:44:10.001Z
---
2026-11-05 Andy: captors attacked right after kidnap wake; "shouldnt do that until you try to escape or steal or do something slaves are not allowed"; woke chained on a roof; wants visible chain/yoke on the neck, "chains can be broken with rocks or flint and repeatedly slamming, and the rope can be sawed through"; not bleeding out after being cleaved in two "should happen within 10s".
PR #1009 MERGED 2026-11-05 12:1xZ as Powers 0.20.95 / Kingdoms 0.7.584 (hint 113), built, not played. Captives may also wake indoors (room to stand) before a roof (Andy). Jars /mnt/project-files/captive-conduct/.
- Kingdoms CaptiveConduct (canAttack spare via ThrallTargetMixin; UNRULY 60 s on strike/break/open container/pick seen/caught at tether), Shackles (TETHER synced, stake beside, chain 24 slams heard 15%/captor within 14, rope 16 strokes seen 30%), client ShackleTether draws it. LifeStory.ground prefers open ground.
- Powers Dismember.HALVE_BLEED_OUT_TICKS=160 for non-lethal halvings (not Rubber-folk/zombies).
My readings: acting up = strike/break/steal/pick-in-sight; old saves' shackled players have no tether drawn.
Related: [[reincarnation]], [[leg-cleave-halve]], [[deeds-captives]].
