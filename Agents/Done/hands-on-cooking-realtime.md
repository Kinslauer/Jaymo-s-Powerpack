---
status: done
task: hands-on-cooking-realtime
branch: claude/hands-on-cooking-realtime
with:
pr:
mods: kitchen
versions:
started: 2026-11-17
owns:
- research only; no files owned yet
---
# Hands-on Cooking / Realtime Minigames Research

**Task:** Research Hands-On Delight's approach and apply it to Powers/Kingdoms/Kitchen cooking — make cooking hands-on and realtime, like minigames. Andy wants "our minigames into realtime like this. All cooking should be hands on."

## Current Cooking System (Kitchen mod) — verified against the code

Kitchen cooking is ALREADY hands-on and in-world; there is no `CookingScreen` GUI. Sessions run per player: the client plays the minigame and reports back over a custom payload, and the server judges quality (anti-cheat).

### Stations (cooking/Station.java enum)
- Vanilla: CAMPFIRE, FURNACE, SMOKER, BLAST_FURNACE
- Cooking for Blockheads oven: STOVE, STOVE_POT, STOVE_PAN, STOVE_FULL (a lit oven; a Cooking Pot / Frying Pan on its hob)
- Cutting board: BOARD (Cooking for Blockheads or Kitchen's own)
- Farmer's Delight-style cookware on a fire: HEARTH_POT, HEARTH_PAN (CookwareBlock set on a heat source)

### Dish methods (cooking/Dish.java)
OVEN (baking), GRILL (campfire grill), SMOKE (smoker), SEAR (blast furnace), CHOP (cutting-board minigame), ROAST (spit minigame), STIR (pot-stirring minigame)

### Current flow
1. Player interacts with a station in the world (right-click with the right tool/item).
2. The server starts a per-player session (Minigames.start / Grill.start / Prep.start) and sends a custom payload (SimUpdate / GrillUpdate / PrepUpdate) with STATUS_STARTED.
3. The client plays the minigame — chop under the knife marks, turn the spit a quarter at a time, circle the mouse to stir (pour-to-band + knead for prep) — and reports back.
4. The server validates timing and amounts, judges Quality (PERFECT / GOOD / BURNT / CHARRED / UNDERCOOKED / ruined), awards the dish, sends STATUS_FINISHED. Walking away abandons the cook (burnt/charred).

### Key classes
- cooking/Minigames.java — CHOP/ROAST/STIR logic + quality judging
- cooking/Grill.java — grill minigame (flip, heat surges, oven mode)
- cooking/Prep.java — prep board (pour water/oil to shifting bands, knead; dough and joints)
- cooking/Rolling.java, Sear.java, Smoke.java, Quality.java
- cooking/Dish.java — each dish lists its Station(s) and Method
- delight/ — Farmer's Delight remake: Cooking Pot + Frying Pan over a fire (CookwareBlock), CuttingBoard + knives (Chopping), feasts, cabbage/rice crops, Comfort/Nourishment effects
- brewing/ — Still and Cask (a keg)

### Not in the pack
- Powers and Kingdoms: no cooking at all.
- Farmer's Delight Refabricated is not a confirmed pack dependency (build.gradle lists cooking-for-blockheads; farmers-delight-refabricated not checked).

## Hands-On Delight Approach (from CurseForge description)

- Cooking pot opens in the world, you stir the soup yourself with a spoon
- Chop with a knife on a cutting board
- Skillet fries food
- Sandwiches built on board
- Keg brewing, kettle tea, sushi rolling
- **No menus — blocks open in the world**
- Every recipe of Farmer's Delight and its addons
- **No new blocks or items**

## Gaps vs Hands-On Delight (possible follow-ups, not decided)
- Nothing in our stations moves the camera to the block or shows nearby players what's being cooked (Diegesis' signature). A kettle-tea station (brew, then tip the kettle to fill cups) would match H-O-D closely.
- H-O-D covers every Farmer's Delight recipe; our Delight content is Kitchen's own remake, not Farmer's Delight's recipes.

## Next steps (when Andy wants code — its own topic/PR)
- Show these findings to Andy; pick a station (cutting-board chop or kettle tea) and prototype it in-world.
- Reuse Minigames/Grill/Prep judging logic; keep server-side validation.

## Research Questions

1. **How does Hands-On Delight detect player interactions with blocks?**
   - Likely uses `PlayerInteractEvent` or block `use`/`useOn` methods
   - Custom block entities that track interaction state

2. **How does it render the "in-world" UI?**
   - Could be block entity renderers with custom models
   - Or screen-less rendering via `GuiGraphics` in world

3. **How does it sync state between client/server?**
   - Block entity data + packets
   - Or attachment system like our `Perch`

4. **What stations in our mods need conversion?**
   - **Kitchen**: Campfire (grill), Furnace/Smoker (oven/smoke), Blast Furnace (sear), Cooking for Blockheads oven (stove), Cutting board (chop), Farmer's Delight cookware on fire (hearth pot/pan)
   - **Powers**: Any cooking-related powers?
   - **Kingdoms**: Any cooking?

5. **How to handle the transition from screen-based to in-world?**
   - Keep screen for dish selection? Or make that in-world too (recipe book on block)?
   - Minigames become direct block interactions
   - Need to track per-player state on block entities

## Implementation Strategy Ideas

### Option A: Gradual conversion per station
- Start with cutting board (CHOP) - simplest: right-click with knife, swing to chop
- Then campfire/pot (STIR) - right-click pot with spoon, drag to stir
- Then campfire/spit (ROAST) - right-click spit to turn
- Then oven/smoker/sear - more complex

### Option B: New "HandsOnCooking" system parallel to existing
- New block entities for hands-on stations
- New interaction handlers
- Keep existing screen system as fallback

### Option C: Replace CookingScreen entirely
- All cooking becomes in-world
- Recipe selection via in-world recipe book (like Farmer's Delight's cookbook)
- Much larger refactor

## Technical Considerations

- **Block entity state**: Need to track per-player cooking progress (unlike current server-side sessions)
- **Sync**: Block entity data + custom packets for real-time updates
- **Rendering**: Custom block entity renderers for visual feedback (stirring animation, chopping particles, etc.)
- **Tools**: Spoon, knife, spatula, tongs - could be existing items or new
- **Multiplayer**: Multiple players at same station? Current system is per-player session.

## Next Steps

1. **Examine Hands-On Delight source** (if available) or decompile to understand their approach
2. **Prototype one station** (cutting board CHOP) as proof of concept
3. **Design attachment/packet system** for per-player block entity state
4. **Plan migration path** for all stations

## Notes

- The task says "No code changes yet" - this is research phase
- Current minigame logic (Minigames.java) can be reused/adapted for in-world interactions; server-side judging stays (anti-cheat)
- This run (hermes-b) researched Hands-On Delight, verified the current Kitchen code, and wrote up findings here; the stalled note's "CookingScreen GUI" flow does not match the current code and was corrected above.
- 2026-11-17 08:39 hermes-b picked up the stalled claim; re-verified against origin/master (6bc998e49): cooking/Station.java (CAMPFIRE/FURNACE/SMOKER/BLAST_FURNACE/STOVE*/BOARD/HEARTH_POT/HEARTH_PAN), cooking/Dish.java methods (OVEN/GRILL/SMOKE/SEAR/CHOP/ROAST/STIR/SEQUENCE), cooking/Minigames/Grill/Prep/Quality/Sear/Smoke/Rolling, delight/CookwareBlock+CuttingBoardBlock+StoveBlock, brewing/StillBlock+CaskEntity, and client CookingScreen/LarderGames/GrillView/OvenView/PrepScreen/RollingScreen/SearView/SmokerView all exist as documented. Research complete; no code changes yet (research-only task). SPEC by hermes-a is ready for Andy's implementation decision (Phase 1+2 as first PR).

## Done

- Documented the real Kitchen cooking system (stations, methods, payload-based flow, key classes)
- Analyzed Hands-On Delight from its CurseForge page
- Listed gaps vs Hands-On Delight and next steps
- No code changes made; no PR opened
- 2026-11-17 08:15 No sign of work for 30 min: marked stalled (whoever ran it stopped).
- 2026-11-17 10:31 No sign of work for 30 min: marked stalled (whoever ran it stopped).

- 2026-11-19 20:30 Vault coordinator: research run finished long ago; Andy said build phase 1+2, so the task is open again for a fresh builder.
