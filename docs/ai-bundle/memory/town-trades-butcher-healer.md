---
name: town-trades-butcher-healer
description: Every Kingdoms settlement keeps a butcher and a cleric (healer) via TownTrades; + regional butcher shops/infirmaries; PR #867 MERGED 2026-11-03 20:47Z (Kingdoms 0.7.483)
metadata:
  type: project
  modified: 2026-11-03T19:08:55.823Z
---
2026-11-03 Andy: "too many times i get to a kingdom and they have no butcher or healer".
PR #867 (branch claude/project-thread-4vy9qv), Kingdoms 0.7.483, built not playtested; jar /mnt/project-files/town-trades/.
`TownTrades` checks loaded kingdoms/fiefdoms/villages every 600 ticks; missing butcher/cleric taken by idle townsperson > nitwit > greenest of a vanilla trade with 3+ holders > newcomer moves in (once/day/trade). Only sets profession (meat-hook work is #864).
Andy 19:31Z: "They need their own specific buildings per biome/kingdom" -> JobHalls.Region (GREEN/COLD/DRY/WET/BLOSSOM from Kit.land): butcher = smokehouse/meat stall/shed/timber shop; new `infirmary` entry (brewing stand, cauldron, infirmary cots) per region, realm 11x9 vs village 9x7. Butcher realm 10 / village 6, infirmary 12 / 8. Kitchen meat hooks in butcher halls.
Related: [[butcher-villagers]], [[medicine-tree]], [[visitors-meals]].
