# NPC hip lanterns, held lanterns for NPCs, lantern clink
- **NPCs wear their lanterns at the hip** (after Andy's Traveler's Lantern link; ideas only, none of its code): an NPC on a humanoid body (MCA villagers and the folk, night-watch guards, zombies and the like) with a lantern in its off hand, which is where NightLights puts the light it carries at night, has it hung at the off-hand hip instead, on the same spring as a player's belt lantern: it trails as they speed up, swings forward as they stop, rocks with each step and is nudged clear of the leg. The hand is drawn empty. The lantern stays in the hand on the server, so NightLights' sight checks and LambDynamicLights' light are unchanged.
- **NPCs hold a main-hand lantern** out and swinging with their step, as players already do (HeldItems).
- **Clink**: a hip lantern (a player's belt slot or an NPC's) gives a quiet lantern clink now and then as the leg on its side swings into it on a walk or run, heard within 16 blocks, yours too in first person.
- Classes: WornLanterns (Layer now generic over any humanoid body, also on every HumanoidMobRenderer; extractNpc, clinks), NpcHipLanternMixin (HumanoidMobRenderer.extractRenderState TAIL), HeldItems (npcLantern).
- Not changed: illagers and plain vanilla villagers (their models aren't humanoid) still carry lanterns in hand as before.
- Built (compileClientJava); not played.
