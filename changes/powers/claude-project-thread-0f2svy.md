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
- **The Unseeing** (Andy: "a feral cult, that believes in nonsense ... both the bandage and blindfold need to be
  visible, and the zealous unseeing will attack you when they go blind, as they start to lose their mind"): their own
  robed people (`Unseeing`; MCA draws the pack's villagers, so nothing could be laid on a villager's head), in four
  looks: a follower, blindfolded, gouged (linen over the sockets, blood showing) and zealous (the linen soaked, blood
  down the face). They're blind, so they hear you within 10 blocks, a sneaking player only within 3. A gouged one turns
  zealous one to two Minecraft days after the cut and attacks anyone it hears who still has their eyes (a blindfolded
  or eyeless player is left alone). Hit one and the others near it strike back. Right-click a sane one for a line of
  their creed; they never trade.
- **A service of the Unseeing** (Andy: "you should be able to stumble on a service of the members performing this on
  one of their followers"): now and then at night, out in the wild in the Overworld (not near a village), 22-32 blocks
  from a survival player, three blindfolded Unseeing stand round a follower between four lit candles. They chant three
  lines, then the eldest does one of the two rites: ties a blindfold on, or carves the eyes out (shears, a cry, blood)
  and binds the sockets in linen. The follower thanks them. A player within 20 blocks at the end is tossed a blindfold
  "for when you are ready". Then they live round the spot. Killing any of them breaks it up. At most once every 3 days a
  player (about one night in five). `BlindnessService`.
- **Enclaves** (Andy: "Villagers who have done this start to get shunned by others, meaning you will find unseeing
  enclaves in the wild, in random caves"): in a cave (no sky, below y 50), now and then 16-30 blocks off, 4-7 Unseeing
  (the eldest blindfolded, the rest blindfolded or gouged, a third of the gouged already zealous) round a lit campfire,
  with candles and a few grey and black carpets for beds. They keep within 12 blocks of it. At most once every 5 days a
  player, never two within 96 blocks. `BlindnessEnclaves`.
- `BlindnessDevotion`, `BlindnessDevoutSight` and three client mixins (`jaymospowers.blindness.client.mixins.json`);
  `Scars.Kind.CARVED_EYES`.
- Built; not played. The Unseeing skins are a first try (plain robes). The worn blindfold's texture is a plain dark band (first try).
