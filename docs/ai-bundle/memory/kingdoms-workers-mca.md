---
name: kingdoms-workers-mca
description: Kingdoms 0.5.21 (PR #40 merged 2026-10-12, pack 0.8.53): realm workers are MCA villagers promoted in place, MCA guards count, Prey, miner torches
metadata:
  type: project
  modified: 2026-10-12T05:49:25.300Z
---
2026-10-12 Andy asked: kingdom miners/builders/farmers weren't targeted by hostile mobs; integrate them with MCA (interactable
like MCA villagers, promoted to builder/farmer/guard the MCA way); then "miners should place torches as they go".
PR #40 merged 2026-10-12 as Kingdoms 0.5.21, pack 0.8.53: the work code drives a `Worker`
interface (Folk + MCA villagers via McaWorkerMixin); `VillagerWorkMixin` swaps the brain for work by day; new
professions jaymoskingdoms:builder|farmer|miner|woodcutter; MCA Professions menu buttons (needs MCA monarch rank:
`/mca-admin overrideVillageRequirements Tester true` in tests); guards: went with "MCA guards count" (card unanswered
at the time). `/kingdomsdev stock` fills storage in dev runs. Playtest artifacts in /mnt/project-files/kingdoms-workers/.
See [[mca-in-pack]], [[kingdoms-war-tempers]].
