# Players and NPCs floored less by sweeps, leg blows, wall slams and throws
- Follows "quieter hurt lines and rarer leg knockdowns" (Andy: "Players are knocked on the ground far too easily. lower
  that chance by 80% and require targeted hits to the legs, knockback or explosions", then "npcs can get the same
  resilience") to the other ways a body is put on the ground. NPCs here are mobs built like a person.
- **Leg sweeps** (`Tripped.sweep`: the Mortal Kit kick, the boxing leg sweep, the slide tackle, the Web Slinger brute
  lunge): a player or NPC goes on its back 20% of the time (`SWEEP_CHANCE`; was 100%); when it keeps its feet the move's
  fallback applies (the kick's and the lunge's slow). The boxing sweep's stamina is still spent on a miss; its cooldown
  only starts on a hit. The bolas' bind still always trips.
- **Leg blows on NPCs** (`LegFell`): an NPC hit on a leg or foot for 35% of its health, or a broken leg, now goes down
  20% of the time too (through its sweep; was 100%), as players already do.
- **Falling fist attack**: no longer puts anyone on their back (a blow from above on the body, not the legs).
- **Wall slams** (`Collisions`: knocked back into a wall for 6+ damage): ragdolls a player 20% of the time (was 100%).
  The slam's damage, stagger and shake are unchanged. (Mobs never ragdolled from slams.)
- **Crushing-blow throws** (`BlastGore`): the throw is knockback, so a thrown player or NPC is floored 20% of the time
  (`THROW_FLOOR_CHANCE`), only when Kitchen's heavy-blow roll didn't already count the blow as knockback (no blow gets
  two rolls).
- Unchanged: the downed state from losing hearts, both legs broken or lost, fainting, hard landings, horse crashes, head
  bumps, being carried or thrown by another player, your own forms' moves, Air Combo launches and wrestling takedowns.
- Built; not played.
