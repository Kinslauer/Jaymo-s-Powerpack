# Blindness: the devout path - blindfold, carved eyes, Sky Sight
- **The calling** (Blindness had none): Andy: "The especially devout opt to don a blindfold or carve out their eyes".
  The **Blindfold** (black wool between two string, worn on the head) blinds anyone without the power; a powerless
  survival player who keeps it on for 5 minutes in a row is called to Blindness (a day off the wait, once a day).
  **Carving out the eyes**: sneak and use shears on nothing; the first use warns, a second within 5 s carves them out.
  It costs 3 hearts (never the last), leaves a lasting scar (Carved-out eyes, until a new life) and a powerless player
  awakens to Blindness at once. Only the powerless (whom no other power has called) and Blindness holders can do it.
  Andy: "you must bandage your eyes to prevent bleeding out". The fresh sockets bleed as hard as a lost limb's stump
  and never stop by themselves: a bandage or rag on the head stops them, and then they've healed for good. Two dark,
  empty sockets show on the face from then on (for everyone; hidden under the blindfold or anything covering the face;
  at the stock eye spots, not found per skin). `Bleeding.holdOpen` lets a power keep a cut open like this.
- **The devout's sight**: a Blindness holder who wears the blindfold or has carved their eyes out sees a thinner dark,
  and living things within 16 blocks glow through it on their screen (pale gold; a dull red for the hostile).
- **Sky Sight**: the devout's Void Sight (C, ascended) lifts the view 16 blocks over them, looking almost straight down,
  eased in and out; a ceiling stops it short. The dark thins further and the glow reaches 32 blocks. Blackout and Abyssal
  Echo are unchanged.
- **A service of the Unseeing** (Andy: "you should be able to stumble on a service of the members performing this on
  one of their followers"): now and then at night, out in the wild in the Overworld (not near a village), 22-32 blocks
  from a survival player, three blindfolded members stand round a follower between four lit candles. They chant three
  lines, the eldest carves the follower's eyes out (shears, a cry, blood), binds them with a blindfold and the follower
  thanks them. A player within 20 blocks at the end is tossed a blindfold "for when you are ready". Then they walk off as
  ordinary villagers; the candles stay. Killing any of them breaks it up. At most once every 3 days a player (0.4% a
  10 s check at night, about one night in five). `BlindnessService`.
- `BlindnessDevotion`, `BlindnessDevoutSight` and three client mixins (`jaymospowers.blindness.client.mixins.json`);
  `Scars.Kind.CARVED_EYES`.
- Built; not played. Villagers don't draw head gear, so the members' and the follower's blindfolds don't show on them. The worn blindfold's texture is a plain dark band (first try).
