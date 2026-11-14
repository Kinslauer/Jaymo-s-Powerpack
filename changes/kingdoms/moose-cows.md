# moose herds - Cube Animals' moose are our bull's cows and the young bulls
- **Bulls and cows** (Andy: "keep our bull and the cube moose are cows"): a Kingdoms moose that spawns on its own in the
  swamps and taiga now brings 1 or 2 Cube Animals moose with it, drawn without antlers (their antler scale set to
  nothing). The bull keeps by them: every few seconds, if its nearest cow has got more than 10 blocks off, it walks back
  to her (never while it is after someone). It still charges any player within 15 blocks, so a herd is guarded.
- **Young bulls** (Andy: "the cube moose with horns can be a juvenile moose that you can find fighting each other they
  should be a bit smaller"): a Cube moose with antlers is a young bull, 0.85 the size with antlers about half the usual.
  They spawn in twos and threes in the same biomes. Every 1 to 3 minutes two of them within 24 blocks walk up to each
  other and knock antlers for 8 to 16 seconds (a clack and a shove back on each knock, no damage), then leave off.
- **The herd**: the big bull runs any young bull off that comes within 8 blocks of him while he has a cow within 16: he
  charges, shoves it (no damage) and it runs. Whatever hurts a cow within 24 blocks of a bull (a player not in creative,
  a wolf) has the bull after it. Cows and young bulls are Cube's own animal otherwise: neutral, tamed and bred with
  spruce saplings, ridden with a saddle; a calf takes a parent's antlers (or between), so a herd can raise its own
  young bulls. Tamed or ridden young bulls still spar; one on a lead or with a rider doesn't.
- Cube moose already in a world, and ones from a spawn egg, have antlers, so they become young bulls when next loaded.
  Without Cube Animals the bull spawns alone as before.
- `wild/Moose` (`finalizeSpawn`, `bringCow`, `KeepByCows`, `DriveOff`), new `wild/MooseHerds` (`SparGoal`, the young
  bulls' spawns, the cows' defence). Cube's antler scale is reached by reflection, its size by the vanilla scale
  attribute. Compiled; not played.
