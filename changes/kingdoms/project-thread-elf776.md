# Grimsby's and Varro's later arts, and Two as One
Andy: "okay start on the other spirits", after "I'd like more of the shadow of war combat inspiration for varro, and grimsby,
the capstone being a mode in which you play as if you had celebrimbor's power, and you can use the abilities." The ideas
only: the names, looks and numbers are ours. All of it is on the spirit key (L), at the end of each spirit's move list.
- **Grimsby** (`spirits/GrimsbyArts`): **Over the Shoulder** (level 9: over the foe in front, a cut of 10 in its back, off its
  feet), **Short Road** (11: at the foe you look at within 20 blocks, a cut of 8; a kill makes it ready again at once),
  **Pale Glaive** (13: a sweep all round for 7 that throws and floors everything within 4.5; twice from level 16, three times
  from 19), **Take the Rest** (15: from a foe off its feet or down: 6 hurt, 25 mana and 2 hearts to you). Guard Break now also
  leaves its foe open to Take the Rest.
- **Varro** (`spirits/VarroArts`): **Hauling Hand** (9: drags the foe you look at, up to 24 blocks off, to your feet and off
  its own), **Javelin Heads** (11: each cast changes the tip of his javelins and bombards: plain, fire, frost, venom, light;
  light burns the undead half again as hard), **The Marshal's Voice** (13: foes of 60 health or less within 10 blocks run for
  8 s, greater ones are weakened), **Break** (15: a foe under 35% health in reach fights for you for a minute, then is itself
  again; three at once; not villagers or anything over 100 health; `Thralls.sway`).
- **Two as One** (`spirits/AsOne`, level 18, both): for 30 s the spirit fights through you. Its figure stands full over you;
  every art costs half and is ready in a third of the time; each blow of your own lands half again as the spirit's and every
  fifth sets off a free Pale Glaive (Grimsby) or three javelins (Varro); a blow at you is answered every 2 s (Grimsby turns
  it aside and cuts back, Varro throws the striker down); each kill adds 3 s (to a minute) and takes you to the next foe
  (Short Road) or brings it to you (Hauling Hand); you run and leap, and no fall hurts. 40 mana, four minutes between.
- Built with Gradle. Not played: every number is a first guess, and the arts reuse poses the arms already had.
