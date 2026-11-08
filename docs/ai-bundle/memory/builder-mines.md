---
name: builder-mines
description: Kingdoms builders use Dungeons and Taverns' own mine pit, tavern, well, smithy + D&T-only buildings at run time (PR #1070, 0.7.629); our MinePit fallback; poor nations plain tunnel
metadata:
  type: project
---
Andy 2026-11-06: D&T's miner's pit (nova_structures:remnant/wild_ruin_19 + _19_b) is what builder mines should be; old tunnel only for "poverty nations". Then "cant they just use the dungeon and taverns structure?", tavern too, "do them all".
PR #1070 (Kingdoms 0.7.627): DntMine digs/builds D&T's pit (D&T mod id mr_dungeons_andtaverns, ARR, read at runtime like CtovHalls); MinePit = our own pit when D&T absent; poor = Wealth.worth < MinePit.POOR_WORTH 80 (guess). DntBuildings (variant MARK 0xD7A5<<48): tavern_house_<wood>, well_archy_<wood>, smithy (forrest/ruin smith on odd tries; CTOV even), new sawmill/farmhouse/apiary/firewatch_tower. graveyard too (kingdoms 50+, Andy allowed past 32 plot cap; DntBuildings.MAX_SIDE 40). Skipped: dappled townhall (too big), hamlet (piglin bunker), D&T has no stable. Merged 2026-11-06 (#1070). Not tested in game yet.
