---
status: ready-for-implementation
task: hands-on-cooking-realtime
branch: claude/hands-on-cooking-realtime
with:
  mods: [kitchen]
  powers: []
  kingdoms: []
versions:
  kitchen: 0.5.x
  powers-master: 26.3
  minecraft: 1.21.4
  fabric: 0.16.x
  java: 21
started: 2026-11-17
based-on:
  - E:/Jaymo Vault Live/Inbox/hands-on-cooking-realtime.md
  - E:/Jaymo Vault Live/Agents/hands-on-cooking-realtime.md
  - Research completed by hermes-b (2026-11-17)
---

# Hands-On Cooking / Realtime Minigames — Implementation Spec

## 1. Executive Summary

**Goal:** Convert Jaymo's Kitchen cooking from screen-based minigames to fully in-world, hands-on interactions inspired by [Hands-On Delight](https://www.curseforge.com/minecraft/mc-mods/hands-on-delight). Players interact directly with blocks (stirring a pot with a spoon, chopping on a board with a knife, turning a spit over a fire) without any GUI screens.

**Scope:** Kitchen mod only. Powers and Kingdoms have no cooking mechanics currently.

**Current State:** Kitchen already uses per-player server sessions + client minigames + server validation (anti-cheat). The minigame logic (CHOP, ROAST, STIR, GRILL, SEAR, SMOKE, PREP/ROLLING) is complete and well-tested. What's missing is the **in-world interaction layer** that replaces the current `CookingScreen` GUI.

## 2. Requirements (from Inbox + Research)

| # | Requirement | Source |
|---|-------------|--------|
| R1 | Cooking happens in-world, no menus — blocks open in the world | Hands-On Delight page; Andy's ask |
| R2 | Stir soup in open pot with spoon (direct block interaction) | Hands-On Delight page |
| R3 | Chop with knife on cutting board (swing-to-chop mechanic) | Hands-On Delight page |
| R4 | Skillet/frying pan fries food in-world | Hands-On Delight page |
| R5 | Sandwiches built on cutting board | Hands-On Delight page |
| R6 | Keg brewing, kettle tea, sushi rolling (stretch: new stations) | Hands-On Delight page |
| R7 | All existing Farmer's Delight recipes supported (Kitchen already remade FD content) | Inbox notes |
| R8 | No new blocks or items required — reuse existing stations/tools | Hands-On Delight page |
| R9 | Reuse existing minigame judging logic (server-side anti-cheat preserved) | Research doc |
| R10 | Recipe selection moves in-world (recipe book on block or carried) | Research Q4, Option C |
| R11 | Multiple players can cook at same station (per-player state on block entity) | Research Q5, multiplayer consideration |

**Explicit non-goals for v1:**
- New block/item models (reuse existing)
- Powers/Kingdoms integration (they have no cooking)
- Full recipe-book-in-world (can stay screen-based for v1; see §7)

## 3. Current Architecture to Preserve

### Server-Side (unchanged)
- `Minigames`, `Grill`, `Sear`, `Smoke`, `Prep`, `Rolling` — judging logic, sessions, anti-cheat
- `Kitchen.award()` — item/buff granting
- `Payloads.*Update` packets — client↔server sync

### Client-Side (to replace)
- `CookingScreen` — the full-screen GUI that hosts minigames
- `LarderGames` — CHOP/ROAST/STIR client simulation & rendering
- `Grill`, `Sear`, `Smoke` client handlers — render overlay on screen

### Stations (cooking/Station.java) — unchanged
| Enum | Block(s) | Methods Supported |
|------|----------|-------------------|
| CAMPFIRE | Vanilla campfire | GRILL, ROAST |
| FURNACE | Vanilla furnace | OVEN, SEQUENCE, STIR, SMOKE |
| SMOKER | Vanilla smoker | SMOKE, SEQUENCE, STIR |
| BLAST_FURNACE | Vanilla blast furnace | SEAR |
| STOVE / STOVE_POT / STOVE_PAN / STOVE_FULL | Cooking for Blockheads oven | OVEN, GRILL, SEAR, STIR, SEQUENCE |
| BOARD | Cutting board (Kitchen/CfB) | CHOP |
| HEARTH_POT / HEARTH_PAN | Farmer's Delight cookware on fire | STIR/SEQUENCE, GRILL/SEAR |

### Dish Methods (Dish.Method enum)
| Method | Minigame Class | Current Client UI |
|--------|----------------|-------------------|
| CHOP | Minigames | LarderGames.Chop (screen) |
| ROAST | Minigames | LarderGames.Roast (screen) |
| STIR | Minigames | LarderGames.Stir (screen) |
| GRILL | Grill | CookingScreen grill overlay |
| OVEN | Grill (oven mode) | CookingScreen oven overlay |
| SEAR | Sear | CookingScreen sear overlay |
| SMOKE | Smoke | CookingScreen smoke overlay |
| SEQUENCE | Kitchen (button-mash) | CookingScreen sequence overlay |
| (PREP/Dough/Joint) | Prep + Rolling | CookingScreen prep overlay |

## 4. Target Architecture

### Core Concept: Per-Player Block Entity Attachments

Each cooking station block entity stores a `Map<UUID, CookingState>` tracking every player currently interacting with it. This replaces the global `Map<UUID, Session>` in each minigame class.

```java
// New interface for stations that support hands-on cooking
public interface HandsOnStation {
    void onInteract(ServerPlayer player, InteractionHand hand, ItemStack tool);
    void onStopUsing(ServerPlayer player); // walked away
    Map<UUID, HandsOnSession> getActiveSessions();
    void removeSession(UUID playerId);
}
```

### Packet Flow (adapted from current)

| Direction | Current | New |
|-----------|---------|-----|
| S→C start | `SimUpdate.STATUS_STARTED` via `CookingScreen` open | `HandsOnStart` packet when interaction begins |
| C→S input | Minigame reports via `finish()` from screen | Real-time `HandsOnInput` packets (swing, movement, held-item-use) |
| S→C sync | `SimUpdate.STATUS_COOKING` per tick (Grill) | `HandsOnSync` per tick (heat, doneness, animation state) |
| S→C end | `SimUpdate.STATUS_FINISHED` | `HandsOnFinish` with quality + rewards |

### Rendering
- **No GUI screen** — all visuals via `BlockEntityRenderer` + particle/sound events
- Existing `LarderGames.draw()` logic adapted to `BlockEntityRenderer` (world-space coordinates)
- Item-in-hand animations (spoon stir, knife chop, tongs flip) via existing mixins (`HumanoidModelMixin`, `SkilletFlipItemRenderer`)

## 5. Implementation Phases

### Phase 1: Foundation (1 PR)
**Goal:** Framework for per-player station state + packet infrastructure.

**Deliverables:**
- `HandsOnStation` interface + default impl in `Station` enum
- `HandsOnSession` record (player UUID, dish, method-specific state, startTick)
- Packet classes: `HandsOnStart`, `HandsOnInput`, `HandsOnSync`, `HandsOnFinish`, `HandsOnAbandon`
- Network handler registration in `KitchenClient`/`KitchenServer`
- Block entity mixin/extension for `CampfireBlockEntity`, `FurnaceBlockEntity`, `SmokerBlockEntity`, `BlastFurnaceBlockEntity`, `CookingForBlockheadsOvenBE`, `CuttingBoardBlockEntity`, `CookwareBlockEntity`
- Config toggle: `handsOnCooking.enabled = true/false` (falls back to screen)

**Acceptance:**
- Right-click station with correct tool starts a `HandsOnStart` packet (no screen opens)
- Server creates per-player session on block entity
- Walking away sends `HandsOnAbandon` → server judges as burnt/charred

### Phase 2: CHOP — Cutting Board (1 PR)
**Goal:** In-world chopping with knife.

**Interactions:**
- Right-click placed cutting board with Knife item → start CHOP session
- Left-click (swing) when veg under knife → `HandsOnInput(type=CHOP)`
- Visual: vegetables slide across board texture (BE renderer), knife swing overlay
- Server judges using existing `Minigames.judgeChop()`

**Deliverables:**
- `ChoppingHandsOn` client handler (swing detection, timing)
- `CuttingBoardBlockEntity` stores `Map<UUID, ChopState>`
- BE renderer draws sliding veg + cut marks + feedback pips (port from `LarderGames.Chop.draw()`)
- Knife item model shows chop animation (reuse `SkilletFlipItemRenderer` pattern)

**Acceptance:**
- Player stands at board, holds knife, left-clicks to chop 8 vegetables
- Perfect/good/ragged feedback shown in-world
- Dish awarded on finish; bowl returned for stews

### Phase 3: STIR — Pot on Fire / Stove Pot (1 PR)
**Goal:** In-world stirring with spoon.

**Interactions:**
- Right-click Cooking Pot (CookwareBlock) or Stove-Pot with Spoon → start STIR session
- Mouse movement in circle around pot center → `HandsOnInput(type=STIR, angle, speed)`
- Boil-up events: server syncs `boilTicks > 0` → client shows particle burst
- Visual: pot top-down view in BE renderer (broth, bubbles, spoon, gauge)

**Deliverables:**
- `StirringHandsOn` client handler (mouse-circle detection around block pos)
- `CookwareBlockEntity` (pot) + `StoveBlockEntity` (pot) store `Map<UUID, StirState>`
- BE renderer port from `LarderGames.Stir.draw()` — disc, broth color per dish, bubbles, spoon, gauge
- Spoon item (new or repurpose `BOWL`? Use `ModItems.SPOON` if exists)

**Acceptance:**
- Player circles mouse around pot; needle shows speed; boil-ups require faster stir
- Sticking/slopping tracked; perfect/good/burnt/charred judged
- Multiplayer: two players can stir different pots on same stove

### Phase 4: ROAST — Spit over Campfire (1 PR)
**Goal:** In-world spit turning.

**Interactions:**
- Right-click campfire with Spit (or food-on-spit item) → start ROAST session
- Right-click campfire again (or press key) → turn spit 90° → `HandsOnInput(type=ROAST_TURN)`
- Visual: spit model on fire, four sides with doneness colors, crank animation
- Heat surges synced from server (flame height)

**Deliverables:**
- `RoastingHandsOn` client handler
- `CampfireBlockEntity` stores `Map<UUID, RoastState>` (extends vanilla BE)
- BE renderer: spit posts, meat cube with four faces colored by doneness, fire particles
- Spit item (existing? or food-on-spit stack)

**Acceptance:**
- Four sides cook unevenly; player turns to balance
- Fire heat varies; visual flame height reflects heat
- Perfect = all sides golden; burnt/charred/undercooked judged correctly

### Phase 5: GRILL / OVEN / SEAR / SMOKE / SEQUENCE (1-2 PRs)
**Goal:** Remaining methods.

| Method | Station | Tool | Interaction |
|--------|---------|------|-------------|
| GRILL | Campfire | Tongs | Right-click to flip; serve to finish |
| OVEN | Furnace/Stove | Peel/Paddle | Slide in (right-click), pull out (right-click when done) |
| SEAR | Blast Furnace | Tongs | Press when marker in zone (like current screen) |
| SMOKE | Smoker | Fan | Hold fan (key/item) to raise heat; gusts lower it |
| SEQUENCE | Furnace/Smoker | None | Button-mash recipe steps (port to in-world buttons on block?) |

**Notes:**
- GRIL/OVEN share `Grill` logic; SEAR/SMOKE share their own
- SEQUENCE may stay screen-based for v1 (recipe step UI hard in-world); or use "recipe book on block" later
- Fan for SMOKE: new `ModItems.FAN` or repurpose `BELLOW` item

### Phase 6: PREP / ROLLING — Dough & Joints (1 PR)
**Goal:** Cutting board dough mixing + joint rolling.

**Interactions:**
- Right-click prep surface (slab/counter/cutting board) with Bowl → pour water/oil (hold use), knead (alternate left/right click)
- Rolling: right-click with Pmeh + Paper → grind (circle), sprinkle (hold), roll (drag up/down)

**Deliverables:**
- `PrepHandsOn` client handler (pour hold, knead taps)
- Reuse `Prep` + `Rolling` judging logic
- BE renderer for dough ball, joint paper

### Phase 7: Recipe Selection In-World (stretch, 1 PR)
**Goal:** Remove need for screen entirely.

**Options:**
- Farmer's Delight Cookbook item (already exists in FD Refabricated? No — Kitchen remade FD)
- Recipe book block entity on station (right-click to open page, click recipe to select)
- Held-item recipe selector (scroll while sneaking)

**Decision:** Defer to follow-up task. v1 keeps `CookingScreen` for recipe selection only; minigames are in-world.

## 6. Technical Details

### Block Entity State (per player)

```java
public sealed interface HandsOnState permits ChopState, StirState, RoastState, GrillState, SearState, SmokeState, PrepState {
    UUID player();
    Dish dish();
    Dish.Method method();
    int startTick();
    boolean isActive();
}

public record ChopState(UUID player, Dish dish, int startTick, int nextCut, List<Integer> results, List<Float> cutPositions) implements HandsOnState { ... }
public record StirState(UUID player, Dish dish, int startTick, float omega, int stirred, int stuck, int spilled, int boilTicks, boolean placed, float spoonAngle) implements HandsOnState { ... }
public record RoastState(UUID player, Dish dish, int startTick, float[] done, int down, float heat, int heatTimer, int turnAnim) implements HandsOnState { ... }
public record GrillState(UUID player, Dish dish, int startTick, float[] done, int down, float heat, boolean oven, int nextSurge) implements HandsOnState { ... }
public record SearState(UUID player, Dish dish, int startTick, int round, int perfects, int goods, int misses, float targetPos, float markerPos, float speed) implements HandsOnState { ... }
public record SmokeState(UUID player, Dish dish, int startTick, float heat, int cureTicks, int scorchTicks, int coldTicks, boolean cured, int nextGust, int gustTicks) implements HandsOnState { ... }
public record PrepState(UUID player, Prep.Recipe recipe, int startTick, float waterLevel, float oilLevel, boolean kneaded, int kneadCount, float waterShift, float oilShift) implements HandsOnState { ... }
```

### Packets (extend `Payload` hierarchy)

```java
// S→C: session starts
record HandsOnStart(int dishIndex, int methodOrdinal, BlockPos stationPos, UUID sessionId) { }

// C→S: player input (type discriminates payload)
sealed interface HandsOnInput { UUID sessionId(); }
record ChopInput(UUID sessionId, float cutOffset) implements HandsOnInput { }
record StirInput(UUID sessionId, float angle, float speed, boolean boiling) implements HandsOnInput { }
record RoastTurnInput(UUID sessionId) implements HandsOnInput { }
record GrillFlipInput(UUID sessionId) implements HandsOnInput { }
record GrillServeInput(UUID sessionId) implements HandsOnInput { }
record SearPressInput(UUID sessionId, float markerPos, float targetPos) implements HandsOnInput { }
record SmokeFanInput(UUID sessionId, boolean fanning) implements HandsOnInput { }
record PrepPourInput(UUID sessionId, boolean water, boolean oil, boolean kneadLeft, boolean kneadRight) implements HandsOnInput { }

// S→C: per-tick sync (Grill/Smoke/Sear need this; Chop/Roast/Stir client-simmed)
record HandsOnSync(UUID sessionId, Map<String, Float> values, Map<String, Integer> ints) { }

// S→C: finish
record HandsOnFinish(UUID sessionId, Quality quality, ItemStack[] rewards) { }

// C→S / S→C: abandon
record HandsOnAbandon(UUID sessionId) { }
```

### Server Tick Handler

```java
// In KitchenServerEvents or new HandsOnTicker
public static void serverTick(MinecraftServer server) {
    // Existing Grill.tick() already does this for grill/smoke/sear
    // New: iterate all HandsOnStation BEs, tick active sessions
    // For CHOP/ROAST/STIR: client sims, server only validates on finish
    // For GRILL/SMOKE/SEAR: server sims, sends Sync packets
}
```

### Client Input Detection

| Method | Detection |
|--------|-----------|
| CHOP | `InputUtil.isMousePressed(GLFW_MOUSE_BUTTON_LEFT)` + held Knife item + looking at BOARD |
| STIR | Mouse movement delta while `HandsOnScreen`-less: track angle around block pos in `ClientTickEvents.END` |
| ROAST | Right-click on CAMPFIRE with Spit/food + no screen open |
| GRILL | Right-click on CAMPFIRE with Tongs (flip), Right-click empty hand (serve) |
| SEAR | Key press (configurable) when marker in zone — overlay rendered by BE renderer |
| SMOKE | Hold `USE` with Fan item near SMOKER |
| PREP | Hold `USE` with Bowl (pour), alternate left/right click (knead) |

### Rendering Integration

- Each `HandsOnStation` BE gets a `BlockEntityRenderer` that reads `activeSessions` and draws per-player visuals
- Use `RenderSystem` transforms to draw at block position + offset (like `StoveRenderer`, `CuttingBoardRenderer`)
- Reuse color constants and lerp logic from `LarderGames`
- Particle/sound events triggered by client handler state changes

### Anti-Cheat Preservation

- Server remains authoritative: `finish()` validates `elapsed >= minimumTicks`, input counts match expectations
- Per-player session tied to `BlockPos` + `UUID`; walking away > 8 blocks → `abandon()`
- `HandsOnFinish` only sent after server validation

## 7. Open Questions / Decisions Needed

| # | Question | Options | Recommendation |
|---|----------|---------|----------------|
| Q1 | Recipe selection: keep screen or move in-world for v1? | A) Screen for recipe pick only, then hands-on / B) Full in-world recipe book / C) Held-item scroll | A — minimal scope; screen already exists |
| Q2 | Tool items: reuse existing (Knife, Spoon, Tongs) or add new? | A) Reuse / B) Add `ModItems.KITCHEN_KNIFE`, `SPOON`, `TONGS`, `FAN`, `SPIT` | A — check if items exist; add only if missing |
| Q3 | Multiplayer at same station: separate meat per player or shared? | A) Per-player (current model) / B) Shared pot/spit | A — matches current session model; simpler |
| Q4 | Client sim for GRILL/SMOKE/SEAR: move to client or keep server-authoritative? | A) Keep server sim (current) / B) Client sim + server validate | A — Grill/Smoke/Sear already server-simmed; don't change |
| Q5 | Config for "classic mode" (screen-based) vs "hands-on"? | A) Global toggle / B) Per-station toggle / C) Per-player toggle | A — global config simplest for testing |
| Q6 | Knife/Chop: detect swing via `AttackEntityCallback` or `PlayerInteractEvent.RightClickBlock`? | A) Left-click swing (attack) / B) Right-click use | A — left-click feels like chopping; Knife not a weapon anyway |
| Q7 | Spoon/Stir: how to detect "circle around pot" without screen coordinates? | A) Project mouse to world, angle around block pos / B) Track mouse delta in client tick | A — more immersive; pot center = block pos + (0.5, 1.2, 0.5) |

## 8. Acceptance Criteria (per phase)

| Phase | Criteria |
|-------|----------|
| 1 | `handsOnCooking.enabled=true`; right-click station with tool → no screen; session starts; abandon works |
| 2 | CHOP: 8 veg slide; left-click chops; pips show perfect/good/ragged; dish awarded; multiplayer works |
| 3 | STIR: circle mouse stirs; boil-ups; gauge shows speed; sticking/slopping tracked; dish awarded |
| 4 | ROAST: spit turns; 4 sides cook; fire height varies; perfect = all golden; dish awarded |
| 5 | GRILL/OVEN/SEAR/SMOKE/SEQUENCE: each works in-world; quality matches screen version exactly |
| 6 | PREP: dough pour/knead; joint grind/sprinkle/roll; rewards match screen version exactly |
| 7 (stretch) | Recipe selected without ever opening `CookingScreen` |

## 9. Testing Checklist

- [ ] Single player: all methods produce same quality distribution as current screen version
- [ ] Multiplayer: 2+ players at same station type (different blocks) — no interference
- [ ] Multiplayer: 2 players at SAME block (e.g., double stove) — separate sessions
- [ ] Walk-away abandonment: burnt/charred judged correctly
- [ ] Disconnect mid-cook: session cleaned up; no ghost state
- [ ] Config toggle off → `CookingScreen` works exactly as before
- [ ] Resource pack compatibility: custom pot/spit models render in BE renderer
- [ ] Performance: 20 players cooking simultaneously → no TPS drop > 2ms/tick

## 10. File Tree Impact

```
kitchen/src/main/java/com/jaymoskitchen/
├── cooking/
│   ├── HandsOnStation.java           (new interface)
│   ├── HandsOnSession.java           (new sealed interface + records)
│   ├── ChopHandsOn.java              (new client handler)
│   ├── StirHandsOn.java              (new client handler)
│   RoastHandsOn.java                 (new client handler)
│   GrillHandsOn.java                 (new client handler)
│   SearHandsOn.java                  (new client handler)
│   SmokeHandsOn.java                 (new client handler)
│   PrepHandsOn.java                  (new client handler)
│   Minigames.java                    (reuse judging)
│   Grill.java                        (reuse logic)
│   Sear.java                         (reuse logic)
│   Smoke.java                        (reuse logic)
│   Prep.java                         (reuse logic)
│   Rolling.java                      (reuse logic)
│   Station.java                      (add HandsOnStation impl)
├── delight/
│   CookwareBlockEntity.java          (add Map<UUID, HandsOnState>)
│   CuttingBoardBlockEntity.java      (add Map<UUID, HandsOnState>)
│   CookwareBlock.java                (onUse → start hands-on)
│   CuttingBoardBlock.java            (onUse → start hands-on)
├── net/
│   payloads/
│   │   ├── HandsOnStartPayload.java
│   │   ├── HandsOnInputPayload.java
│   │   ├── HandsOnSyncPayload.java
│   │   ├── HandsOnFinishPayload.java
│   │   └── HandsOnAbandonPayload.java
│   └── Payloads.java                 (register new payloads)
├── client/
│   ├── HandsOnClient.java            (new: input detection, BE renderer registry)
│   ├── HandsOnRenderer.java          (new: shared renderer utils)
│   ├── delight/
│   │   ├── CookwareRenderer.java     (extend: render active sessions)
│   │   ├── CuttingBoardRenderer.java (extend: render active sessions)
│   │   └── StoveRenderer.java        (extend: render active sessions)
│   └── CookingScreen.java            (modify: only for recipe selection)
└── JaymosKitchen.java                (config toggle + client/server event reg)
```

## 11. Dependencies

- **Cooking for Blockheads** (required): STOVE stations
- **Farmer's Delight Refabricated** (optional): if present, use its cookbook; Kitchen remade FD content so not strictly required
- **No new mods** for v1

## 14. Versioning

- Target Kitchen `0.6.0` (minor bump for major feature)
- Config key: `handsOnCooking.enabled` default `true` for testing, `false` for release until Andy approves

---

**Prepared by:** hermes-a (Apollo)
**Date:** 2026-11-17
**Status:** Ready for Andy's review → assign to implementation bot when approved
**Next Action:** Show this spec to Andy; pick Phase 1+2 as first PR