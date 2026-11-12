# Broken loot chests spill their loot
- **Broken Lootr chests no longer swallow their loot.** Lootr keeps each player's share of a loot chest or barrel apart
  from the block, and dropped none of it when the chest was broken (sneak-break), so whatever was inside just vanished.
  Now every share in it spills onto the floor as a random roll: each stack comes out 60% of the time, keeping 40-100% of
  its count; the rest is lost in the smash. A chest you never opened spills a roll of your own share, so forcing a locked
  chest still gets you something, just less than picking the lock. Creative breaks spill nothing. Plain chests drop all
  they hold, as before. Tuning in `SpiltLoot` (`SURVIVES`, `KEPT_MIN`).
- Built; not played.
