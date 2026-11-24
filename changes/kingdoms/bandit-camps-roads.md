# Bandit camps by the trade roads
- The structure placement plan's bandit camp ("beside a busy road between two settlements, in cover; keeps away from
  watchtowers"). Now and then, while a player travels a road whose two ends both stand, a band of masked bandits pitches
  a camp 14 to 26 blocks off it: in a clearing with trees round it, far from both settlements and every other camp, out of
  sight. None where a watchtower (`towers_on_borders`) may stand within 12 chunks, and at most 2 near any one settlement.
- The camp is a barbarian camp's tents, fire and loot pile with a chief and 4 bandits in masks, leather and blades (the
  bandit holds' own dress), growing by one every 2 days up to 9 and bringing in bread, leather, arrows and a little coin.
  They stand with the other bandits; killing the chief clears it with the bandit holds' message.
- `BanditCamps` (new), from `Camps`' spawn check and daily growth; camps of kind `bandit` that aren't in an Illager
  Structures place.
- Built in CI; not played.
