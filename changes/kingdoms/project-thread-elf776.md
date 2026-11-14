# the Reaper steps out for its kills
Andy: "the reaper model from MCA needs to come out and do the execution with a random weapon but usually his scythe, same for
when he kills ghosts".
- **The Reaping** (L at one of the downed, from the Reach) is no longer an instant kill. The Reaper leaves its keeper's back,
  stands over the downed, draws its weapon back and brings it down, and the blow lands as the swing ends (2.5 s, as a ghost's
  harvest is). Everyone sees it, as they see it take a ghost. One kill at a time: L does nothing more at the downed until it
  is done.
- **Its weapon** (`Reaper.weapon`, `McaReaper`): six times in ten its scythe (MCA's own); otherwise a greatsword, a greataxe, a
  warhammer or a halberd, the same for every viewer of that kill. They are ours: boxes on the same hand, so MCA's own poses
  swing them, with our own steel-and-wood texture (`textures/entity/reaper_weapons.png`). The same roll is made for each ghost
  it takes.
- The weapon now hangs drawn back a little longer and comes down just before the blow (85% of the way through, was 70%).
- Built with Gradle. Not played: the four weapons' shapes and how they sit in the hand are set by eye from the scythe's
  numbers.
