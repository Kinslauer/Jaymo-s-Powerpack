---
name: camp-workers-mca
description: Camp (barbarian/pirate/orc) foremen and builders are MCA villagers since Kingdoms 0.7.481 (PR #866); before that camps were excluded and stayed folk
metadata:
  type: project
  modified: 2026-11-03T18:25:26.515Z
---
2026-11-03 Andy: "Foremen and builders are not villagers and do not seem to have the correct animations" (clip in a barbarian camp).
Cause: Workers.inPlace and McaCompat.becomeVillager excluded FactionType.BARBARIAN, so camp crews stayed Folk.
PR #866 MERGED 2026-11-03 19:2xZ (Kingdoms 0.7.481): camp workers are MCA villagers; newcomers take the folk's blood and hides (McaBridge.campDress/wearLike); camps never draft a realm's spare villager; Barbarians.isCampVillager includes camp workers (campWorker) so raiders spare them. Not playtested.
Related: [[kingdoms-workers-mca]].
