---
name: floating-riders
description: Kingdoms 0.5.32 (PR #56 merged 2026-10-12 15:30Z): Folk riders floated over horses; fixed with ridingOffset(-0.7F) on the folk EntityType
metadata:
  type: project
  modified: 2026-10-12T15:24:35.480Z
---
2026-10-12 Andy reported (Medal clip) mounted NPCs floating high above their mounts. Cause: the jaymoskingdoms:folk
EntityType had no vehicle attachment, so riders' feet sat on the saddle point. Fix: `.ridingOffset(-0.7F)` in
JaymosKingdoms.onInitialize (zombie/skeleton value in 26.3). Branch claude/fix-floating-riders-i2sp3x, PR #56 (merged on Andy's "merge pr"),
Kingdoms 0.5.32 / pack 0.8.65 (merged first, so symbiote PR #55 must take 0.8.66+). Test jar
/mnt/project-files/floating-riders/. Built only, not seen in game.
Related: [[kingdoms-caravans]], [[kingdoms-rickshaws]].
