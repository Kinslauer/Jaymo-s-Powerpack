# Medieval picks, patch 3: Hearing

- Every monster hears now, each with its own ears, not only zombies: skeletons, spiders, illagers, witches, the night's creatures, and a camp's orcs, raiders and bandits go to where a noise came from. A few deaf kinds (slimes, magma cubes, shulkers, guardians, the clear cube) stay deaf (`jaymoskingdoms:deaf`). (`Hearing`, `Awareness`)
- Walls muffle sound by what they're made of: stone a lot, wool more, wood and glass less, an open door, trapdoor or gate hardly at all. A ray through the blocks for every listener in range, borrowed with credit from Mob Senses (Shamanalle, MIT), capped per tick so the server stays easy. (`SoundRay`)
- Footsteps by ground: wool and carpet are quiet underfoot, gravel and metal loud, wet ground louder; Feather Falling boots and Swift Sneak leggings quieten your steps. Walking and running are heard by every monster now, together with Quiet Step and a Ranger's quiet tread. (`Footsteps`)
- Rain outdoors and storms cover your noise, and a loud sound near you (thunder, a bell, a blast, a gunshot) drowns quieter steps for a moment. Bells carry far.
- Decoys: an item you drop or toss makes a noise where it lands: a bell or iron carries far and pulls guards and monsters to it, wool and feathers land soft. Tossed coins and thrown weapons count too (always the real item, spent).
- Monsters look first, then come: a faint sound at the edge of hearing makes one stop and turn its head and body toward it; a second sound there brings it over. A searching mob looks about the spot a while, then walks back to where it was. The awareness eye half fills while one listens for you.
- A monster that hears you calls its friends near, and they fan out round the spot in a ring instead of piling onto one block; a band's leader shouts it and his call reaches further. A zombie horde's groan spreads them round you too.
- Gunshots carry far (44 blocks); a TaCZ gun with a suppressor only 16. Handgonnes, arquebuses, muskets, pirates' pistols, blunderbusses and cannons are black powder and never quiet. Where a ball or bullet strikes far off, it makes noise there too, enough to lure a horde away.
- With Simple Voice Chat, monsters hear you talk: a shout carries far, a whisper only close, walls muffle it. Loudness is measured against your own normal voice, so a quiet microphone isn't free stealth. At night guards hear trespassers talking, and come to see. (`VoiceRange`)
- Blood trails for every hunter: below half your health you leave a blood trail that spiders, wolves and werewolves follow as well as zombies (`jaymoskingdoms:smells_blood`); a werewolf taking a scent finds a bleeding mortal first, from twice as far. (`ScentTrails`, `SpiderMinds`)
- A fuller stealth meter round the awareness eye, bars and icons only: how far your noise carries, how lit you are, whether you're bleeding, and when you're whispering, talking or shouting. It shows while you sneak, bleed or talk. (`SensesClient`)

Built; not played.
