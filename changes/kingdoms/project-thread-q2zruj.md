# goblin scouts and the ghosts at fresh graves
From two Blockbench models Andy uploaded (goblin-scout, ghost).
- **Goblin scout** (`GoblinScout`, drawn from the model with its own idle, walk, attack and cackle): every goblin village
  sends out 2 when it rises and keeps up to 3 (30% a day to replace one), ranging up to 40 blocks from its fire. A scout
  sees 32 blocks (the village's goblins see 20). When it first sees one of the village's prey it stops and cackles for
  1.4 s, and every goblin of its village within 40 blocks with no fight of its own comes for what it saw; it won't call
  again for 30 s. Then it fights: 14 health, quick, a 3-damage dagger jab, and its buckler turns aside half the arrows
  shot at its front. A player passing for a goblin (the mask) is left alone. With its village cleared it runs wild and
  goes for any player. Drops arrows and now and then a gold nugget. Spawn egg.
- **Ghosts** (`GraveGhost`): for 3 nights after a bearer buries someone in a settlement's graveyard, their ghost stands
  over the mound from dusk to dawn while a player is within 40 blocks: the model's see-through pale blue figure, bobbing,
  lit from within, watching whoever comes by. Nothing hurts it and it hurts nothing. Right-click it for a few words; give
  it any flower or a candle and it thanks you and goes for good (12 experience, +2 reputation with its settlement).
  Left alone it stops coming after the third night. Ghosts are never saved; each night's is raised from the grave record.
  Spawn egg (that one just waits for morning).
- Lore: "Goblin scouts" and "The nights at the grave" in Lore.md, approved by Andy.
- Credits: Blockbench Workshop, CC BY 4.0 (Andy); the makers' names are still to be filled in.
- Compiled (`compileClientJava`); not played. Unseen in game: the scout's size and animation timing, the ghost's
  see-through look, whether MCA goblins answer the cackle.
- **A ghost's voice** (`VoiceMasks.Mask.GHOST`, `VoiceRange`). Andy: "the voice changer automatically makes you sound
  like a ghost if you speak otherwise it is muted." A player out of their body, left for dead (Powers), speaks with a ghost's
  voice through Simple Voice Voice Changer, forced like a curse: thin, a little high, wavering, mostly echo. The voice
  comes from where the ghost is, not the body, and the ghost hears what is said near it though its body lies out of
  earshot. A ghost whose client has no voice changer is not heard at all. Group calls and text chat are left alone.
  Not heard in game: the ghost voice's numbers are guesses.
