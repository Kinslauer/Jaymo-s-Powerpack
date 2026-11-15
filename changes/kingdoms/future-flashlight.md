# Gunmen carry less shot and resupply from powder garrisons
- Andy: "mobs with guns should spawn with less ammo". A musket or pistol carrier starts with 3 to 5 shot (was 6 to 10),
  and a caravan guard with 2 to 3 balls for his musketoon (was 3 to 5) (`MobAmmo.SHOT`, `MobAmmo.BALLS`). Gunmen who
  already counted theirs in a world keep their old count; new ones get the smaller one.
- **Powder garrisons** (Andy: "There should be ways for them to resupply, either from powder garrisons in kingdoms or
  looting", and "It shouldnt replace the storehouse"): kingdoms and fiefdoms raise a **Powder Magazine** as they grow
  (from 20 people), their own squat stone building apart from the storehouse, with one door, no windows, its
  torches outside and barrels of powder round the walls (`PowderMagazine`). Shot and balls no longer trickle back from
  nothing; they're made from gunpowder, 4 shot or 2 balls a measure. A realm's gunman within 48 blocks of the magazine
  draws a measure from its barrels every 30 s while nothing is after him, and one who's run dry walks back to it. An
  empty magazine, or none yet, gives nothing (`MobAmmo.garrison`).
- **Keeping it filled**: the realm's weaponsmith tops the magazine up to 24: first he carries over gunpowder the
  storehouse got in trade (realms now ask for powder in trade, `TradeRoutes`), else he mills it, a coal or charcoal and
  a bone making 2 (`Jobs.POWDER_KEEP`). Anyone who gets inside can take from the barrels.
- **Looting**: anyone short of shot or balls picks up gunpowder lying about (a fallen gunman's, a creeper's) and goes
  looking for it within 16 blocks once dry, the way archers fetch arrows.
- Fixed: powderheads never topped up a dry musketeer, because their check looked for shot in the musketeer's hand
  (`MobAmmo.carries`).
- Built; not played.
