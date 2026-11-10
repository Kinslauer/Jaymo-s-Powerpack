# Titan voices for the Great Ape and every xeno form, carrying to the ground; giants hear the ground; spilt blood lies still
- **Titan voice** (Andy: "a transformed great ape, and every xeno form, gets a titan voice changer turned on"). In any of
  the Xeno-Band's alien forms, Simple Voice Voice Changer's own Titan voice is now forced on, as the werewolf's and the
  Great Ape's voices are (the ape keeps its deeper, growling titan). It's the voice changer's own effect on the
  speaker's client, so nothing new had to be written for the sound. `VoiceMasks.Mask.TITAN`, `VoiceChangerCompat`,
  `PowersCompat#xenoForm`.
- **A titan's voice reaches the ground** (Andy: "audible to everyone on the ground: their voice range must reach players
  far below them"). The ape and every xeno form now speak 16 blocks further than everyone else, plus 2 blocks for every
  block they stand taller than a man (a full Great Ape: about 74 more, so about 122 blocks with Simple Voice Chat's
  default 48). A whisper stays a whisper.
- **A giant hears the ground** (Andy: "make sure a transformed great ape still hears the players on the ground"). Voice
  chat listens from the camera, and a full ape's camera rides about 40 blocks from his feet, so the players around him
  were too far to hear. Every voice that reaches a player a block or more taller than a man now reaches him with 1.4
  blocks more range for every block of his extra height, so he hears the ground about as a man standing at his feet
  would. This goes for any giant player, not only the ape.
- Both go through `VoiceRange`, a plugin on Simple Voice Chat's server API (its `VoiceDistanceEvent`, and its
  per-listener sound packet event), made only by Simple Voice Chat itself (Kingdoms' `voicechat` entrypoint). Simple
  Voice Chat is all rights reserved: only its published API is used, compiled against and never shipped by Kingdoms.
  Tuning: `VoiceRange.TITAN_RANGE` / `SPEAK_PER_BLOCK` / `HEAR_FROM` / `HEAR_PER_BLOCK`.
- **Spilt blood lies still** (Andy: "blood particles on the ground keep re-animating every few seconds"). The zombies'
  blood scent (`Awareness`) sent two drops at every spot of a hurt player's blood once a second for a minute, and since
  Powers turned those drops into small spatters, each played its splat again on the ground over and over (Andy's clip at
  2 hearts). The scent is no longer shown again: where the blood falls it now leaves one of Powers' lasting stains, in
  the player's own blood, which lies still, dries darker and can be tracked like the rest. Zombies still smell it as
  before. Tuning: `Awareness.DRIP_STAIN` / `WOUND_STAIN`.
- Built; not played. Not tested: voices with Simple Voice Chat on the Deck (how loud a titan sounds from far off, and
  how the ape hears the ground in third person and in first), the Titan voice on a xeno form, the stains under a
  bleeding player.
