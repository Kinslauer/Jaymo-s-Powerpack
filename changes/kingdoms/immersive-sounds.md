# Wolves and guards hear footsteps
- `Footsteps`: every half second a moving player's feet carry 6 blocks walking, 12 running, times the ground (metal 1.4, gravel 1.3, wood 1.2, snow 1.1, stone 1, mud 0.9, grass/sand/moss 0.7, wool 0.4), +2.5% per armour point, the sneak share every other noise uses (`Awareness.noise`, so Quiet Step counts), the stance wheel's low crouch (x0.7) and crawling (x0.4), and half through a wall.
- Wild wolves (tag `jaymoskingdoms:hears_footsteps`: wolves and foxes) that hear it come to look (the search for a lost player); tame ones turn their heads. Guards (realm, camp and MCA guards and archers) turn to the sound for 2 s; asleep, feet that carry 8+ blocks may wake them as a chest's creak does (`ChestNoise.wakeBy`). Creative and spectator players make none.
- `PowersCompat.lowStance` reads Powers' low crouch.
- Built; not played.
