# GT New Horizons picks, patch 2 - hungry jumps, waking hungry, Time Bloom on slow farms
- **Jumping makes you hungry** (`stamina/Exertion`): each jump adds 0.1 hunger exhaustion on top of vanilla's, armour
  or none (`JUMP_EXHAUSTION`). A jump within half a second of spending feathers on a move (a dodge, vault or wall
  jump: `Feathers.lastSpend`) has paid already and costs nothing more.
- **Wake up hungry** (`core/wounds/Mortality`): one left for dead wakes with no more than half a full stomach
  (`WAKE_FOOD`), saturation no more than that; so does one whose life ended, when they respawn.
- **Time Bloom** (`power/chrono/TimeBloom`, `BloomFarms`, `compat/KingdomsCompat.hurryTrees`): with Jaymo's Kitchen,
  the bloom's random ticks aren't slowed by Kitchen's half-speed farms, and the animals in it grow up, come back into
  breeding, lay and give milk again 20 times as fast. With Jaymo's Kingdoms, its growing trees in the bloom grow on
  20 times as fast too. Both are found by reflection; without them it works as before.
- Built; not played.
