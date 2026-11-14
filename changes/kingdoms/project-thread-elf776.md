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
- **Ambrose** (`spirits/AmbroseArts`): **Shared Ward** (9: you and every friend within 8 blocks get 4 hearts of absorption and
  Resistance for 15 s), **Lay Low** (11: the foe you look at within 14 blocks has no fight in it for 10 s; over 100 health it
  is only weakened), **Hearthlight** (13: a pool 5 blocks across for 12 s; friends in it mend, the undead in it burn),
  **Stand Again** (15: you and every friend within 8 blocks are mended 4 hearts and rid of every harmful effect and fire).
- **Thessaly** (`spirits/ThessalyArts`): **Binding Script** (9: the foe you look at and every foe within 5 of it cannot move
  for 4 s), **Return to Sender** (11: every arrow and thrown thing near you turns back on whoever sent it), **Marginalia**
  (13: every foe within 16 glows through walls for 20 s and your next blow on each lands half again), **Transposition** (15:
  you and the creature you look at, up to 24 blocks off, change places; whatever hunted you loses you).
- **The Quiet One** (`spirits/QuietArts`): **Thread** (9: drawn along a line to a wall or ledge, or onto a foe with a cut),
  **Shroud** (11: the foe you look at is wrapped where it stands for 5 s), **Hidden Blade** (13: a thrust that lands 2.5
  times as hard from behind or into a wrapped, floored or downed foe), **Many Hands** (15: tendrils lash every foe within 6,
  three times).
- **Two as One** is every spirit's at level 18 now. Its free art every fifth blow, its answer to a blow at you and what a kill
  leads to are each spirit's own (Ambrose: Shared Ward, he soaks the blow and talks the striker down, a kill mends everyone
  near; Thessaly: Binding Script, the striker is held, a kill reads and binds the next; the Quiet One: Many Hands, the hidden
  blade answers, a thread takes you onto the next).
