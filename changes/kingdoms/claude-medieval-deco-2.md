# the wind pushes - drift in the air, rolling items, arrows, shelter, sound
- **Wind push** (`sailing/WindPush`, `client/disasters/WindFeel`; ported in spirit from Global Wind, MIT, notice in `ports/globalwind/`): the world's wind (`Storms` through `Winds`) now moves things. Grounded creatures feel nothing; anyone airborne (a jump, a fall, an elytra glide, the Wind power's flight) drifts a little downwind from a fresh wind up, less the bulkier they are (by body size, so Stoneborn drift less than Sylvans). Creative and spectators aren't pushed. Each client moves its own player; the server moves mobs and items.
- **Items** roll downwind in a gale, and on ice from a lighter wind. **Arrows and thrown shafts** bend a little in any wind (the old gale rule made gradual, same strength at the top).
- **Shelter**: a short ray (8 blocks) upwind from head height; a solid block, glass included, means shelter, so standing behind a wall you feel nothing, and a ship lying in a cove catches less (`Ship`'s sail push and her drawn sails).
- **Sound** (`client/disasters/WindSounds`): a low rush that follows the wind's strength, a thin whistle in a hard wind, quieter in shelter and indoors.
- Not done: the hooded cloak and banners leaning in the wind (needs 26.3 render-state work). No weight system exists, so only bulk scales the drift.
- Built by CI; not played.
