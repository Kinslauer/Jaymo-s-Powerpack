# Seat compounds: castle seats generated whole with their first town
- **Why kingdoms stopped sprawling**: since kingdoms come only from Epic towns, and Epic towns only stand in plains,
  desert, savanna, snowy plains and taiga, most of the land never got a kingdom; fiefdoms hang on kingdoms (a Grim hold
  within 600 of a seat, 2 per kingdom), so they thinned out with them.
- **Seat compounds** (`SeatCompounds`, structure `jaymoskingdoms:seat_compound`, set every ~36 chunks, kept 8 chunks off
  villages and 24 off the world's middle): in any overworld land but sea, rivers, beaches and bare peaks, a seat castle
  from the pack's structure mods (`GrandSeats.CHOICES` by the land: ATi's marble chateau in the plains, woodland keep in
  the north, Catalonian castle or granite fort in the dry lands, the jungle fortress...), and round it the town's first
  pieces drawn in the land's materials with the builders' own designs: a paved square with the bell, benches and lamp
  posts; a hedged garden with a fountain; the barracks; 3-5 homes. Doors face the castle; the ground under all of it is
  levelled (beard_box), and laid after the trees grow, so trees standing on a piece are cleared. The castle's spawners and loot are taken out as it generates.
- **Founding**: the first time a player nears its bell, a compound with no realm within 320 becomes a kingdom (throne in
  the castle, as a town's seat); near a kingdom with fewer than 2 fiefdoms, that kingdom's fiefdom; else a village allied
  to the nearest kingdom. Its pieces are held from the builders' tearing down, and the barracks and homes count as
  built, so the builders grow the town outward from there. Epic towns still found kingdoms as before.
- **Epic towns the most common village** (Andy: "can you make epic towns spawn the most often?"): each Epic town may
  stand on all the dry land of its kind, not just its biome tag (`EpicTowns.lands`, `StructureBiomesMixin`): forests,
  meadows, jungles and swamps take the plains town, badlands the desert's, snowy groves and taiga the snowy one; no sea,
  rivers, beaches, peaks and slopes, mushroom islands or caves. In the shared village pool each weighs 30 against the
  other mods' 1-5, so where their villages also suit the land an Epic town is still drawn about two times in three.
  Dev server with Epic, Towns and Towers, Repurposed Structures and Dungeons and Taverns, 169 /locate points on one seed:
  Epic towns were 32 of 85 land villages before, 62 of 84 after. Ocean villages (80, Towns and Towers' and Repurposed
  Structures') are unchanged.
- `Buildings.Kit.wild` (a kit before any settlement stands), `GrimSites.foundFief` takes its announcement.
- New worlds/terrain only. Dev server (with ATi): compounds generate in plains/forest (marble chateau) and desert (Catalonian castle), every piece in place, bell at the square. Founding (needs a player) not tried; not played.
