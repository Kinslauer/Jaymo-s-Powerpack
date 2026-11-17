---
status: running
task: hands-on-cooking-realtime
branch: claude/hands-on-cooking-realtime
with:
pr:
mods: kitchen
versions:
started: 2026-11-16
owns:
---
# Hands-on Cooking / Realtime Minigames Research

**Task:** Research Hands-On Delight's approach and apply it to Powers/Kingdoms/Kitchen cooking — make cooking hands-on and realtime, like minigames. Andy wants "our minigames into realtime like this. All cooking should be hands on."

## Current Cooking System (Kitchen mod)

### Stations (Station.java enum)
- **Vanilla**: CAMPFIRE, FURNACE, SMOKER, BLAST_FURNACE
- **Cooking for Blockheads oven**: STOVE, STOVE_POT, STOVE_PAN, STOVE_FULL (lit oven with pot/pan on hob)
- **Cutting board**: BOARD (Cooking for Blockheads or Kitchen's own)
- **Farmer's Delight cookware on fire**: HEARTH_POT, HEARTH_PAN (Cooking Pot/Frying Pan placed on campfire)

### Dish Methods (Dish.Method)
- OVEN (baking in furnace/smoker/oven)
- GRILL (grilling on campfire/grill)
- SMOKE (smoking in smoker)
- SEAR (searing in blast furnace)
- CHOP (chopping on cutting board - minigame)
- ROAST (spit roasting over campfire - minigame)
- STIR (stirring in pot - minigame)

### Current Flow
1. Player right-clicks station → opens `CookingScreen` (GUI)
2. Select dish from list → server starts session (`Kitchen.start`)
3. **Minigames** (CHOP/ROAST/STIR): Client plays `LarderGames` in screen, reports results via `SimUpdate`
4. **Grilling/Smoking/Searing**: Client simulates in screen, server validates
5. Server judges quality → awards dish

### Key Classes
- `Kitchen.java` - Server-side session management
- `Minigames.java` - CHOP/ROAST/STIR logic (client + server judging)
- `Grill.java`, `Sear.java`, `Smoke.java` - Other cooking methods
- `CookingScreen.java` - Client GUI (856 lines, complex phases)
- `LarderGames.java` (inner classes) - CHOP/ROAST/STIR game implementations

## Hands-On Delight Approach (from CurseForge description)

- Cooking pot opens in the world, you stir the soup yourself with a spoon
- Chop with a knife on a cutting board
- Skillet fries food
- Sandwiches built on board
- Keg brewing, kettle tea, sushi rolling
- **No menus — blocks open in the world**
- Every recipe of Farmer's Delight and its addons
- **No new blocks or items**

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
- Need to understand Hands-On Delight's implementation before designing ours
- Current minigame logic (Minigames.java) can be reused/adapted for in-world interactions
- Server-side judging logic should remain similar (anti-cheat)

## Done

- Documented current Kitchen cooking system (stations, methods, flow, key classes)
- Analyzed Hands-On Delight approach from CurseForge description
- Identified research questions and implementation strategy options
- No code changes made