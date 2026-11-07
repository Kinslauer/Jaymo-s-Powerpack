---
name: kingdoms-rickshaws
description: Kingdoms 0.5.26 rickshaws (PR #50 merged 2026-10-12): pullers haul gatherers' loads, errands, townsfolk, carriage-less rulers, paid player rides
metadata:
  type: project
  modified: 2026-10-12T08:40:41.049Z
---
2026-10-12 Andy asked for villagers who rickshaw goods and people around villages and kingdoms; said "build it" with the
defaults: players can hire rides (emerald fare to the treasury), pullers are a new profession (Folk.PULLER = 12,
jaymoskingdoms:puller, MCA Professions button). Classes: Rickshaws (+ Wagon.RICKSHAW, Carriages drives one for a ruler
without a carriage, Gatherers/Farmers.handOver). Branch claude/project-thread-qj0wzg, draft PR #50, Kingdoms 0.5.26
(stables #49 must now take 0.5.27+). Test jar /mnt/project-files/rickshaws/. Follow-up 2026-10-12: pullers tire through the day (slower, breathers, reset by sleep) and a client pose leans them into the shafts (PullerPose); realms with treasury > 300 keep a horse-drawn cab (Cabs, Wagon.CAB) that takes the player fares instead of rickshaws (my default: rickshaws there send players to the cab). Built only, not seen in game yet.
Related: [[kingdoms-caravans]], [[kingdoms-stables]].
