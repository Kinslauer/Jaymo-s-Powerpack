# Smoke and Flame Avatar track: the skull, the burning chain, Judgement, burning steeds
Ideas only from another mod's fire rider (All Rights Reserved): every line of code and every texture here is our own.
Built and compiled, not played. Check in game: the skull fades in over the char between 55% and 85% flame and back out as
the bar drops; a Smoke user's flail and whip light what they hit; sneak + Z on a guilty player; a steed's ride over lava.
- **The skull under the fire**: as a Flame Avatar's flame bar burns high the face burns away to a bare skull wreathed in
  fire, and comes back as the flame drops (`avatar_skull.png`, drawn over the char in `FlameAvatarClient`).
- **Burning chain**: a Smoke user's flail and whip burn. Every blow sets the creature alight, the whip's wrap keeps it
  burning, and the flail's whirl no longer knocks things away: it wraps what it clips, drags it in and swings it round
  you. The head glows with embers (`Lash`).
- **Judgement**: an Emberborn who has become a Flame Avatar and has Smoke can seize a creature's eyes (sneak + Z, held, 16
  blocks) and let the harm it has done burn it, through armour, for up to 3 seconds. A player burns by the weight of
  their life (killing villagers and players, crimes against a realm, outlaw standing); an outlaw burns for half;
  everyone else feels nothing, and the move does not start (`AvatarJudgement`, `Karma.guilt`).
- **Burning steeds**: horses with a mane and hooves of fire, found at the Flame Avatars' camps in the Nether (Kingdoms
  raises two at each new camp and keeps up to three). They are tame, saddled, 35% faster, immune to fire, leave a trail
  of flame, and gallop over water and lava. Only a Flame Avatar or a Smoke user can ride one; anyone else who climbs on
  is set alight and thrown (`BurningSteed`).
