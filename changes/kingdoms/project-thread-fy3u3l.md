# digger pits - lids that match the ground, a digger waiting below
- **Lids match the ground**: a pit's trap cover no longer has its own pale texture. It is drawn with the model of the block
  it replaced (grass with the biome's tint, dirt, coarse or rooted dirt, podzol, mycelium, moss, mud, sand, red sand,
  gravel, snow, stone), so it looks the same as the blocks beside it, and it shows as that block's colour on maps. Where
  the ground is none of those the lid copies a neighbour; where no neighbour fits either, no pit is dug there. A lid placed
  by hand copies the ground beside it. Lids in worlds made before this draw as grass.
- **A digger waits in each pit**: a nook is dug into one wall of every pit, level with the stakes, and a digger sits in it.
  It keeps to its nook, attacks whoever drops into the pit (through the lid or after digging it out), and walks back to its
  nook when the fight is over. The lid giving way still calls every digger within 24 blocks.
- The warren's own diggers (now 1-2, since each pit has one) start down in the chamber instead of on the mound.
- The keen-eye hint (Notice: a few sparkles and a rustle only the player who spotted it gets) is unchanged.
- Classes: `TrapCoverBlock` (`LOOK`, `Ground`), `Warrens.pit/lurker`, `Digger.lurk/watchPit/ReturnGoal`, `CreaturesClient`.
- Compiled (`compileClientJava`); not played. Only warrens generated after this get the nooks.
