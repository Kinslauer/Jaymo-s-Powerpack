# gore left behind - entrails, organs, ribs, spine, bone and tatters from bodies blown apart or eaten
Andy: "It doesnt make much sense to me that the tiger ate me and left nothing, not even tatters, so explosions should gib
with the limbs like they do, but drop the offal piles as entrails and any other gore peices you can get from the mod."
- **Blown apart**: with the chunks of its own model, a body now throws what was inside it: 2-3 organs, 2 ropes of gut, a
  slab of ribs, a length of spine and 2-4 bone shards (more from a wide body, fewer from a narrow one), and its entrails
  land around it as **gut piles** (the butcher's offal piles: 2 pieces from a man-sized body, up to 6), set down a tick
  after the blast so the crater doesn't take them. Scavengers smell the piles and they rot away as any gut pile does.
  Players, villagers, illagers, witches and the risen dead also throw 1-3 scraps of cloth.
- **Eaten**: a body a predator tears open (`Scavengers`, werewolves' devouring too) leaves ribs, spine, 1-2 bones, a rope
  of gut, one gut pile and, if it was clothed, 2-3 tatters, lying 5 minutes (a player's 10) before they sink away.
- **Who has what** (`Gibs.Insides`): organs and gut are drawn in the body's own blood colour (Powers' `bodyBlood`: ender
  purple, a zombie's rotten brown; plain red without Powers). Skeletons throw bone only. Machines, creepers, anything
  with no blood, fish and small critters, spiders, the new `no_innards` tag (slimes, silverfish, bees, shulkers,
  phantoms...) and anything narrower than 0.35 blocks throw nothing extra. A carcass already gutted on the hook has
  bones but nothing soft.
- The pieces are `Gib`s with a `piece` kind, drawn by `GibRenderer` from our own models and `textures/entity/gore.png`
  (`tools/gen_gore.py`). Ideas from Gore Death (melamain, MIT, Forge 1.20.1): organs, spine, bone shards, per-creature
  rules. No code or art taken. CBC Explosive TNT (the other link) has no gore in it.
- None of it is drawn for a player with Powers' Blood setting on Off.
- Compiled; not played.
