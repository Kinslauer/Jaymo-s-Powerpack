# the minotaur's labyrinths - it keeps the Epic dungeons
- **Labyrinths**: every large Epic Structures dungeon (plains, snow and desert) holds one minotaur in its biggest hall,
  and about one in three medium ones does too. Small dungeons have none. It is stood there the first time a player
  comes within 40 blocks of the hall, once per dungeon, never in peaceful, and it keeps to within 14 blocks of the spot.
  Dungeons in worlds that already exist get theirs the same way, the first time someone goes back in.
- Nothing in the Epic Structures files is changed: `beasts/Labyrinths` reads the dungeon's rooms from the world and picks
  a roofed, clear floor near the middle of the biggest one. Which dungeons count is two structure tags,
  `jaymoskingdoms:minotaur_labyrinths` (always) and `minotaur_labyrinths_some` (`SOME_CHANCE`, 35%).
- Compiled; not played. The room check was run against the mod's room files (each large dungeon's first hall has dozens
  of spots that fit), but no dungeon has been walked in game.
