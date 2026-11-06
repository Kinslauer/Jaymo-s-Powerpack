---
name: mca-court-wizards
description: Court wizards are MCA elders, merged PR #88 as Kingdoms 0.5.53 / pack 0.8.104; elder = grown 8+ days, tracked by us since MCA has no old age
metadata:
  type: project
  modified: 2026-10-13T04:00:00.494Z
---
2026-10-13 Andy: "court wizards should be integrated as MCA villagers, maybe once they grow old".
PR #88 MERGED 2026-10-13 04:03Z on Andy's "merge" (Kingdoms 0.5.53 / pack 0.8.104): with MCA, a kingdom's court wizard is the eldest spare MCA
villager (grown >= 8 days; `Enlisted.grownSince`, adults first seen backdated 0-16 days). `CourtWizards` class;
profession jaymoskingdoms:wizard (cleric robe, client hat layer, staff). Folk wizards retire; necromancers stay folk.
Skill-tree respec: `CourtWizards.is(entity)`; MCA wizards via sneak+right-click (coordinator's call).
Not playtested. Jar: /mnt/project-files/court-wizards/. Related: [[kingdoms-sorcery]], [[kingdoms-workers-mca]], [[skill-tree]].
