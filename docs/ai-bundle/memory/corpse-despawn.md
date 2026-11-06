---
name: corpse-despawn
description: PR #36 merged (Kitchen 0.4.28, Kingdoms 0.5.23, pack 0.8.55): skeletons rise, fire spread, guards burn monster corpses, fire pits
metadata:
  type: project
  modified: 2026-10-12T07:15:37.488Z
---
2026-10-12 Andy scrapped a 20-min hostile-corpse despawn and instead asked: all skeletons reanimate like zombies; guards
in towns and kingdoms burn mob corpses or throw them into fire pits / lava; burning corpses ignite nearby wood; iron
golems only throw corpses (never burn); builders add fire pits; sheep (like pigs) drop leather 15% when butchered.
PR #36 merged 2026-10-12 on Andy's "merge pr". Kitchen: `rises()`, `spreadFire`, public `carryBy` (refuses heavy)/
`throwAt`/`isHostile`. Kingdoms: `Undertakers` (guard folk, MCA guards via Workers.near, village iron golems) +
reflection `KitchenCompat`; `Buildings.firePit` ("fire_pit"). Not playtested. Jars: /mnt/project-files/corpse-despawn/.
See [[golem-corpses]], [[kingdoms-workers-mca]].
