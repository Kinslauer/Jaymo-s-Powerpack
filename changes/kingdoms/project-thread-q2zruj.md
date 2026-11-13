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
- Credits: yair (@yairtalmy2111), Blockbench Workshop, CC BY 4.0.
- Compiled (`compileClientJava`); not played. Unseen in game: the scout's size and animation timing, the ghost's
  see-through look, whether MCA goblins answer the cackle.
- **A ghost's voice** (`VoiceMasks.Mask.GHOST`, `VoiceRange`). Andy: "the voice changer automatically makes you sound
  like a ghost if you speak otherwise it is muted." A player out of their body, left for dead (Powers), speaks with a ghost's
  voice through Simple Voice Voice Changer, forced like a curse: thin, a little high, wavering, mostly echo. The voice
  comes from where the ghost is, not the body, and the ghost hears what is said near it though its body lies out of
  earshot. A ghost whose client has no voice changer is not heard at all. Group calls and text chat are left alone.
  Not heard in game: the ghost voice's numbers are guesses.
- **Rune-bound colossus** (`remnants/RuneColossus`, yair's model with its own rise, walk, run, two blows, rune ring, flinch
  and fall). Andy: "these can spawn among the high field giant as fodder to make it a bit more difficult." Pieces of the
  Highfield monolith: 3 climb out of the ground 9 to 16 blocks from the Highfield Giant when it first has someone to
  fight, and 2 more at two thirds and at one third of its health. Each is 3.5 blocks tall, 40 health, 8 armour, slow: an
  overhead smash (10), a hook (7), and every 15 s or so its runes ring out (4 damage, a throw and Slowness II within 5
  blocks). It can't be hurt while rising, the Giant's own blows pass it by, and when the Giant falls they all go to
  pieces. Drops calcite and gold nuggets. Spawn egg (that one just fights). The other three Giants have none.
- **Crystal beetle** (`wild/CrystalBeetle`, yair's model). Andy: "these should spawn in amethyst geodes." A beetle of the
  geodes under the three rules for living things. It turns up by itself in a geode near a player that has none (looked at
  every 20 s), never leaves it, and bites (2) only what hurts it. Every 2 minutes it gnaws the furthest-grown bud within
  6 blocks down one stage, and what it eats grows on its shell: up to 4 amethyst shards, taken off with shears without
  hurting it. After 6 meals what it leaves behind turns a plain amethyst block near it into a budding one; two beetles
  with 4 meals each lay another, up to 4 to a geode. A beetle that finds nothing to eat neither seeds nor lays. Killed, it
  drops a shard at most. Spawn egg.
- **Barrow knight** (`barrow/ZombieKnight`, yair's Zombie Knight). Andy: "this zombie knight needs a different head and
  should wield a sword from our pack, and correctly." Its head is our own now (a dead villager's face under a rusted
  kettle helm, `kingdoms/tools/gen_zombie_knight.py`) and the modelled sword is gone: he holds a real item, Powers'
  forged iron sword (a plain iron sword without Powers), drawn in his fist the way the game seats any held item
  (`BbMobRenderer.holding`, which any Blockbench mob can now use). 30 health, 10 armour, an overhead hew every 1.3 s for
  the blade's own damage, 8% to leave the sword. Undead. Spawn egg.
- **Cryptbound revenant** (`barrow/Revenant`, yair's model at 0.62, 2.7 blocks tall). The knights' lord: 120 health, 12
  armour, a cleave (12), a shield blow that throws you back (4), and at two thirds and one third of his health his oath:
  the blue light rings out 6 blocks (5 magic damage) and 2 more knights climb out of the floor (4 at most about him).
  Drops a soul lantern, iron and bones. Undead. Spawn egg.
- **The old barrows** (`barrow/Barrows`). The Overworld's buried cobblestone rooms (a spawner on cobblestone, in no
  structure): when a player first comes within 40 blocks, half of them get 2-3 knights, and 30% of those the revenant
  too, fixed by where the room is. They keep to the room. None of them goes for a player holding a lantern or soul
  lantern in either hand unless that player strikes one.
- Lore: "(new) The pillar's stone", "(new) Crystal beetles" and "(new) The barrow's oath" in Lore.md, for Andy to approve.
- Compiled; not played. Unseen in game: the colossus' size beside the Giant and whether five to seven are too many,
  the beetle's pace, how its shell reads with no crystal stages drawn (the count isn't shown), how the knight's sword
  sits in his fist through his swing, and whether the revenant fits the smaller rooms.
