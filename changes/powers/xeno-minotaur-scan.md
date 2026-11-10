# Xenoform: Asterion, the minotaur alien
- **Asterion** (Tiv's ask): scanning Kingdoms' minotaur now teaches its own alien, a bull-headed brute with the
  minotaur's own build and moves, in a slate hide with the band's green glow and its great hammer. The minotaur is off
  Tusk's DNA list. 1.6x, 48 health, hammer blows of 8, a little slow, hard to knock about.
- **Charge** (hold R): head down, it runs where it faced and only leans a little to steer (never more than 12° off its
  line). It smashes through dirt, wood and leaves, whoever placed them; stone and anything else solid stops it dead. The
  first creature it meets is caught on its horns and carried in front: stunned at every impact, hurt harder for every
  block it's put through, and run through when a wall stops the charge (a torso wound that bleeds). Others in the way
  are flung aside. Nothing can hurt Asterion while it charges (and half a second after) except the carried creature's
  own blows: a carried mob swings back with whatever it holds, a carried player can just hit it. Let go to throw the
  catch off ahead.
- **Roar** (hold G): the longer it's held (up to 2.5 s), the further (3 to 14 blocks) and harder the shockwave that runs
  out when you let go; whoever it reaches is thrown back limp. You crawl while you hold it, and a long roar takes longer
  to come back.
- **Slam** (V): the hammer comes down and everyone round it is hurt and thrown off their feet. **Gore** (B): the horns
  into the creature in reach, a bleeding cut and a toss.
- **Big weapons**: Asterion swings its great hammer, or any Better Combat two-handed weapon in your main hand (claymores,
  war axes, glaives, spears, scythes...) with that weapon's own damage and moves; every other alien stays fists-only.
- Look: "Minotaur" by eanncrod (Blockbench Workshop, CC BY 4.0), the model Kingdoms' minotaur uses, baked with its
  animations and its texture repainted (`tools/gen_xenoform_asterion.py`; credit in `art/xenoform_asterion-CREDITS.md`
  and `textures/entity/bb/CREDITS.txt`). Its Ultimate is the plain fallback for now (Labyrinth).
- Classes: `forms/Asterion` (all its numbers at the top), `XenoForm.wields`, `XenoformHandsMixin`, `XenoDna`; client
  `AsterionModel`, `XenoformClient` (act clock, held weapon), `XenoformLayer`.
- Built; not played. Not seen in game: the model's size and animations, the weapon in its hand, the first-person arm,
  the charge through walls, carrying a player, and Better Combat weapons while changed.
