---
name: village-life
description: Village Life (ARR Forge mod) ideas remade in Kingdoms 0.7.46 / pack 0.11.5, PR #214 MERGED: festivals, village pets, couriers, hunger/weariness
metadata:
  type: project
  modified: 2026-10-15T02:25:34.619Z
---
2026-10-15 Andy asked for Village Life (curseforge villagelife, VillageLife+, Forge 1.20.1, All Rights Reserved) features in our systems.
PR #214 (branch claude/project-thread-kpddrv), unplaytested: `Festivals` (own festival ~every 8 days, `/kingdom festival` for lords at 32 emeralds, `/kingdom festival now` for ops),
`Pets` (cats/dogs tamed with a villager as owner, attachment `village_pet`, dogs never bite villagers), `Couriers` (`/kingdom deliver <player>`),
`Spirits` (hunger when storehouse has 0 food, evening yawns). Routines.free now excludes festival-goers/couriers; Routines.freeOf is the rest.
Skipped as already covered: names, families, moods, rep, talk (MCA); routines/neighbours; mayor = our rulers/lords. Jar: /mnt/project-files/village-life/.
