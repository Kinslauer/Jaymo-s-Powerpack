# Players and NPCs floored less by heavy blows, leg shots and run-overs
- Follows "heavy blows floor players far less" (Andy: "Players are knocked on the ground far too easily. lower that
  chance by 80% and require targeted hits to the legs, knockback or explosions", then "npcs can get the same
  resilience"):
- **NPCs and heavy blows** (`MobRagdolls`): a mob built like a person (its ragdoll is a humanoid's: villagers, MCA folk,
  illagers, zombies, skeletons...) is knocked limp by a heavy blow only as a player is: a blast, or a blow that knocks it
  back (`LivingRagdolls.knocksBack`), and then 20% of the time (`NPC_FLOOR_CHANCE`; was every heavy blow). Animals and
  other bodies, and hard landings, are unchanged.
- **Leg shots and blows on a broken leg** (a player's arrow or full swing low): a player or an NPC goes down to crawl
  20% of the time (`PERSON_DOWN_CHANCE`; was 100%); otherwise they only get the limp, with no fall thud. Animals still
  always go down.
- **Run over** by a boat, cart or ridden mob: floors a player 20% of the time (was 100%), one roll per run-over (not
  one per tick of contact; 1 s before the next roll).
- `MobRagdolls.humanoid`, client `LimpController` (`RUN_OVER_CHANCE`, `RUN_OVER_ROLL_TICKS`).
- Built; not played.
