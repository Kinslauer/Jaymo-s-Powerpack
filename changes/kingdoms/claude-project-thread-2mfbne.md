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
- `Buildings.Kit.wild` (a kit before any settlement stands), `GrimSites.foundFief` takes its announcement.
- New worlds/terrain only. Dev server (with ATi): compounds generate in plains/forest (marble chateau) and desert (Catalonian castle), every piece in place, bell at the square. Founding (needs a player) not tried; not played.
