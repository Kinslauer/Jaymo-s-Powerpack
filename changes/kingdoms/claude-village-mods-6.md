# Village mods patch 6: small structures (cairns, tree sites, mazes, wayside inns, cave gardens, plan plot outline)
- **Cairn caches** (rc_cairn): heaps of field stones in four shapes on hills, moors, taiga and plains, within about 500
  blocks of a settlement (they mark the ways out of it; `PlacementReasons` rule `cairns_by_ways`). An arm's length under
  each is a chest of what travellers leave (`chests/cairn`: bread, torches, string, nuggets, now and then emeralds). Now and
  then a rumour names the nearest cairn instead of a treasure (`Cairns.hint`, from `Rumours.tell`).
- **Tree structures** (rc_trees, `TreeSites`, `Crow`): in the forests, a giant hollow oak with a hermit living in a room
  inside it (bed, books, lectern, a ladder up to a loft and a chest). The hermit greets visitors with a scroll as the other
  hermits do, with talk of their own about the wood (`Hermits`' new oak hermitage). Hollow oaks stand far from anything
  lived in (`hermitages_apart`). In the taiga, a great spruce holds a bandit lookout: a plank platform high up with a rail,
  a ladder, lanterns and a chest of bandits' loot. Two bandits from a nearby road bandits' camp man it; if there is no camp
  near, a camp rises a little way off (`lookouts_by_ways`). Near towns, a hanging tree with a chain and a skull on its long
  bough, and a gallows post whose sign carries the Sun's hanging list (`gibbets_by_towns`). One or two crows sit on the
  bough. A crow flies from strangers, pecks at the remains and calls low. Offer one seeds or meat and it remembers you, and
  once a day near its tree it drops you a small trinket. Take the remains down and the crows leave for good.
- **Mazes** (rc_maze, `HedgeMaze`, `CryptMaze`, `MazeGen`): now and then a castle's grounds hold a hedge maze, with gravel
  walks between clipped hedges, one gate on the castle's side and a small fountain at its heart. Under every great
  necromancer's crypt there is a stone maze, reached by a spiral stair in the hall's corner. At its heart are soul lanterns
  and a chest (`chests/crypt_maze`: bones, candles, a little gold, now and then an enchanted book). On dark nights, while
  nobody is inside, its walls come down and go up again in a new pattern every few minutes, always with a way from the stair
  to the heart, and the stone can be heard grinding from outside. Once the necromancer is destroyed the walls stop moving.
- **Wayside inns** (rc_inn, `WaysideInns`): every long King's Road gets an inn about halfway along it, built unseen a little
  off the road with a path to it. It is a two-storey timber house with a hearth, a counter, barrels, tables and three
  bedrooms upstairs. Beside it is a stable with three stalls, hay and water troughs. Out front are a lamp post and a sign
  with the inn's own name, and in the yard a bounty board that the nearer town's folk post to (`Bounties.courtAt` falls
  back to the inn's road). An innkeeper lives there and greets you once a day; if they die, another comes after a few days.
- **Cave gardens** (rc_flora, `CaveGardens`): rare moss-floored grottos 16 to 24 blocks under the old woods and lush caves,
  reached by a mossy shaft with a ladder that opens in the turf. They hold a pool with big dripleaf, berried cave vines,
  spore blossoms, candles and lanterns, a rooted-dirt path and a composter, and stand far from anything lived in. In the
  largest an old druid may live in a nook. While the druid lives the garden is tended: picked berries, carpets, mushrooms
  and snuffed candles come back where it's lit, and moss creeps over bare stone next to moss. It tends faster while someone
  who brought the druid a gift that day is near. With no druid, the garden runs down as it's picked.
- **Design preview** (rc_preview, `MasonPreview`, `MasonTable.plot`): holding a written building plan outlines its plot. Before
  it's laid, a pale blue box shows where it would go against the block you look at, at the plan's size as turned. Once laid,
  a warm box marks its plot. The plan's head edge is drawn heavier, so turning the plan shows.
- The fixed designs (cairns, the three trees, the inn, the cave gardens) are drawn once in `tools/small_structures.py` and
  `tools/small_designs/`, which write the templates (`structure/smalls/`), their worldgen and the preview pictures from the
  same blocks.
- Built; not played. Compiled by CI only; nothing was generated or seen in a world.
