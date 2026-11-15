# Gunmen carry less shot and resupply from powder garrisons
- Andy: "mobs with guns should spawn with less ammo". A musket or pistol carrier starts with 3 to 5 shot (was 6 to 10),
  and a caravan guard with 2 to 3 balls for his musketoon (was 3 to 5) (`MobAmmo.SHOT`, `MobAmmo.BALLS`). Gunmen who
  already counted theirs in a world keep their old count; new ones get the smaller one.
- **Powder garrisons** (Andy: "There should be ways for them to resupply, either from powder garrisons in kingdoms or
  looting"): shot and balls no longer trickle back from nothing. They're made from gunpowder, 4 shot or 2 balls a
  measure. A realm's gunman draws a measure from his realm's storehouse every 30 s while he's within 48 blocks of it
  and nothing is after him; one who's run dry walks back to it. An empty storehouse gives nothing (`MobAmmo.garrison`).
- **Stocking the garrison**: a realm's weaponsmith mills gunpowder while its storehouse holds under 24 (a coal or
  charcoal and a bone make 2, `Jobs.POWDER_KEEP`), and realms now ask for powder in trade (`TradeRoutes`). Players can
  stock it too.
- **Looting**: anyone short of shot or balls picks up gunpowder lying about (a fallen gunman's, a creeper's) and goes
  looking for it within 16 blocks once dry, the way archers fetch arrows.
- Fixed: powderheads never topped up a dry musketeer, because their check looked for shot in the musketeer's hand
  (`MobAmmo.carries`).
- Built; not played.
