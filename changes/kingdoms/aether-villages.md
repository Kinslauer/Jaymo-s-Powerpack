# sky folk - who lives in the Aether, the sky lord's court, and balloon ships
Andy: the sky villages are "filled with divine born, any other heritage has a reason for not only having traveled but being
accepted"; the halfway folk are "what the dawnborn culture becomes".
- **Blood** (`SkyFolk.born`, from `VillagerHeritage.roll`): everyone born in the Aether is Divine-born, but for 15% who
  are Dawnborn, the halfway folk.
- **Nobody is minded up there** (`Prejudice.minds`): a villager standing in the Aether holds no one's blood against them.
- **Their talk**: five lines each for the Divine-born and the Dawnborn of the sky, used while they are in the Aether
  (`lang/parts/sky_folk.json`). Hints only.
- **Things that fall off** (`SkyFolk.fall`): about once in six days spent under an open sky in the Overworld, something
  drops near you: a dropped letter (five of them), somebody's hat, cloudwool, a moa feather or cloudtwine.
- **Their clothes** (`SkyFolk.outfits`, art from `tools/gen_sky_folk.py`, 16 new outfits): the Divine-born of the
  Aether wear white robes with gold at the collar, cord and hem; the halfway folk sun-coloured tunics with a white sash
  and a sun on the chest; the sky lord a blue mantle over the robe; his wardens a white tabard over deep blue. A villager
  is dressed as soon as their blood is settled. Travellers from below keep their own clothes.
- **The sky lord's court** (`SkyCourt`): each sky castle near a player in the Aether gets a lord (the one seated on the
  throne when it was built if he can still be told, else whoever stands nearest the keep) and three wardens, who take
  up MCA's guard trade. They have their own lines. A dead lord is replaced from the castle's people at the next look.
  The court is not a kingdom: factions live only in the Overworld and their wars, caravans and envoys walk, so the
  castle holds no land and takes no part in them.
- **Balloon ships** (`Ship`, `Hull.airworthy`): a ship with at least 24 blocks of wool hung over her, enough to lift her
  weight (half a block of water's weight per block of wool), flies. Her wheel casts her off with no water under her;
  at the wheel Space takes her up and Sprint down (0.12 blocks a tick), W/S set her cloth and A/D steer as at sea. She
  holds her height with nobody at the wheel, moors in mid-air when you sneak off a still wheel, settles onto water if
  brought down on it, and is never becalmed head to wind. Enough of her cloth shot away and she falls. The readout
  shows her height.
- **Sky ferries** (`SkyFerries`): in daylight, about every five minutes, two villagers of a sky village near a player go
  aboard the skiff at its mast, take her up 22 blocks, cross 110 to 200 blocks of sky, come about, return and haul her
  in onto her mooring, where she is blocks again. You can ride along on her deck or take her wheel. Something in her
  way sends her 6 blocks higher; a trip that runs past four minutes turns for home.
- Compiled; not played. Not seen: whether MCA villagers sit quietly as a ferry's crew, and how the skiff looks under way.
