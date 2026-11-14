# the Reaper steps out for its kills
Andy: "the reaper model from MCA needs to come out and do the execution with a random weapon but usually his scythe, same for
when he kills ghosts".
- **The Reaping** (L at one of the downed, from the Reach) is no longer an instant kill. The Reaper leaves its keeper's back,
  stands over the downed, draws its weapon back and brings it down, and the blow lands as the swing ends (2.5 s, as a ghost's
  harvest is). Everyone sees it, as they see it take a ghost. One kill at a time: L does nothing more at the downed until it
  is done.
- **Its weapon** (`Reaper.weapon`, `McaReaper`): six times in ten its scythe (MCA's own); otherwise a greatsword, a greataxe, a
  warhammer or a halberd, the same for every viewer of that kill. Andy: "use the real models please": these are the pack's
  real items (Powers' iron claymore; Balkon's WeaponMod iron battleaxe, warhammer and halberd), drawn in the scythe's hand so
  MCA's own poses swing them. Only where an item isn't in the game does a plain box weapon stand in. The same roll is made
  for each ghost it takes.
- The weapon now hangs drawn back a little longer and comes down just before the blow (85% of the way through, was 70%).
- **What the Reaper raises gets up as itself** (Andy: "they should always reanimate as the corpse that died, i killed an mca
  shaman and he revived as a player zombie"). Reap and Death's Company used the necromancers' table, which turns every
  person's body into a zombie. Now the body rises as the mob it was, from the corpse's own saved look (`Thralls.raiseAs`):
  the same villager with the same name, face and gear, a wolf as a wolf. Nothing with more than 150 health rises, and never a
  player's body. A risen villager follows its keeper and goes for whoever they fight (a risen caster casts for them:
  `Reaper.risenStep`, `Casters.foe`); a risen beast with no attack of its own closes and bites for 4. The Death Sorcerers'
  and court necromancers' raising is unchanged.
- **The risen smoke black** for as long as they stand, not only as they get up.
- Built with Gradle. Not played: how each real weapon sits in the hand is set by eye from the scythe's numbers, and a risen
  MCA villager's fighting leans on MCA's own brain.
