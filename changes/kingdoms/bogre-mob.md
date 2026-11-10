# The Bogre: a solitary swamp ogre
- **The Bogre** now lives in mangrove swamps: a huge, slow ogre using Inhabitants' model, texture and animations (JeremySeq / Team Synapse, art by Catalyst, MIT; credit in `kingdoms/art/bogre-CREDITS.md`). The behaviour is our own, written after Inhabitants'.
- It leaves unarmed people alone. Come within 12 blocks with a weapon out (sword, axe, spear, bow, crossbow, trident, or anything that hits harder than a stone tool) and it stands and roars once, shaking your view. Stay armed within 6 blocks after that, or hit it, and it roars again and fights. It forgets a warning after a minute.
- Its only blow is a hammer slam: it runs at you, swings when you're within 4.5 blocks, and the slam sends a shockwave 6 blocks out (18 damage at the centre, less at the edge, throws you up and away). Jump it and it misses.
- It keeps to itself: it walks away from villagers and other Bogres, and never spawns within 96 blocks of another Bogre or near a village. One at a time, persistent, 200 health.
- It sleeps through the night when no one's near (sneaking lets you get within 2.5 blocks before it wakes), wakes at dawn or when hit, and dances while a jukebox plays near it.
- Drops bones, bone blocks, leather and mushrooms. Spawn egg added. Its cooking and the Giant Bone Club are separate patches.
- Classes: `inhabitants/Bogre` (new), `Inhabitants`, `client/inhabitants/InhabitantsClient` (`BogreRenderer`). Tuning numbers are the constants at the top of `Bogre`.
- Built; not played. Spawning, the slam's reach against the model's swing, and the animations haven't been seen in game.
