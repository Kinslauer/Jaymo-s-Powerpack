# siege train - war parties bring a mangonel, rich realms a field gun
- Andy: "can we have warring kingdoms bring siege engines?" (after Siegeworks: its code is LGPL but its art is all rights
  reserved and it has no 26.3 build, so these are our own engines, code and model).
- **The siege train** (`SiegeTrain`): a realm's war party with soldiers enough (it keeps 3 marching beside each crew of 2)
  brings an engine. Most bring a **mangonel**; a realm with 1000+ in its treasury (the ones that keep wall cannons) brings
  a **field cannon** instead; in a great war such a realm brings both. Camps and villages bring none.
- **On the march** (`Haul`): two of the party's soldiers push each engine behind the march. It only rolls while one of
  them is within 5 blocks, follows the ground a block at a time, steers round what stops it, and is lifted over what it
  can't get round after 5 seconds. Its wheels turn.
- **At the siege**: the mangonel is set down 34 blocks short of the enemy's seat and throws a stone every 10-13 s, high
  over the walls, at enemy guards, archers and hostile players 12-46 blocks off (`Mangonel`, `SiegeStone`: 12 damage,
  2.5 block burst, knockback). The field cannon is set down 20 blocks off and its first crewman sits to it; it picks its
  own mark when he has none. Their shots never hurt their own realm's people or a town's ordinary villagers and golems.
- **Nothing is safe** (Andy): a stone or ball breaks what it lands on, the besieged's walls, roofs and players' builds
  included (a stone takes a hole about 2 blocks across, through cobble and stone brick at its middle; a ball a little
  more). With no foe in reach the mangonel batters the enemy's seat itself, its stones falling up to 7 blocks wide.
  Spared: the attackers' own land, chests and other containers, obsidian; and nothing breaks while mob griefing is off.
- **Stopping them**: kill the crew and the engine stands silent where it was left; break it (50 blows' worth for a
  mangonel, 40 for a cannon) and its crew go back to the fight. After the siege the crew push it home. Engines aren't
  saved, like the parties.
- The mangonel is drawn in the cannon's timber and iron (`MangonelRenderer`, no new texture): cocked arm with a stone in
  the cup, thrown against a padded stop, winched back down.
- Compiled (Gradle `compileClientJava`); not played. Untested in game: how well crews keep up over rough ground, and the
  look of the model in the world.
