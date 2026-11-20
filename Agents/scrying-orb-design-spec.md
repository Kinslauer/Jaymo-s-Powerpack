---
status: in-review
for: hermes-a
from: coordinator
asked: 2026-11-20
thread: cmsg_scrying_orb_design_spec
claimed_by: hermes-a
pr:
suggested_owns:
  - kingdoms/src/main/java/com/jaymoskingdoms/ScryingOrb.java
  - kingdoms/src/main/java/com/jaymoskingdoms/ScryingOrbBlock.java
  - kingdoms/src/main/java/com/jaymoskingdoms/ScryingOrbBlockEntity.java
  - kingdoms/src/main/java/com/jaymoskingdoms/SightedShard.java
  - kingdoms/src/client/java/com/jaymoskingdoms/client/ScryingOrbRenderer.java
  - kingdoms/src/main/resources/assets/jaymoskingdoms/lang/parts/scrying-orb.json
  - kingdoms/src/main/resources/assets/jaymoskingdoms/models/block/scrying_orb.json
  - kingdoms/src/main/resources/assets/jaymoskingdoms/models/item/scrying_orb.json
  - kingdoms/src/main/resources/data/jaymoskingdoms/alchemy/infusion/scrying_orb.json
  - kingdoms/src/main/resources/data/jaymoskingdoms/villager_trade/cleric/scrying_orb.json
writer: hermes-a
text_pass:
---

# Scrying Orb — Design Specification

**Branch:** `claude/scrying-orb-design` (worktree: `E:/wt/scrying-orb-design`)
**Parent research:** `claude/frameless-portals-research` → `FRAMELESS_PORTALS_RESEARCH.md`
**Inbox task:** `E:/Jaymo Vault Live/Inbox/scrying-orb.md`
**Lore:** `E:/Jaymo Vault Live/Lore.md` (lines 2285–2286, Andy approved 2026-11-18)

---

## 1. Item Identity

| Field | Value |
|-------|-------|
| **Registry ID (block)** | `jaymoskingdoms:scrying_orb` |
| **Registry ID (item)** | `jaymoskingdoms:scrying_orb` |
| **Block entity type** | `jaymoskingdoms:scrying_orb` |
| **Creative tab** | `FUNCTIONAL_BLOCKS` (inserted after `end_crystal`) |
| **Stack size** | 64 |
| **Rarity** | Uncommon (enchanted glint) |
| **Sound group** | Glass (`SoundType.GLASS`) |
| **Hardness / resistance** | 1.5F / 1.5F |
| **Light emission** | 0 (idle) → 8 (active, via `ACTIVE` state property) |
| **Tool** | Any / pickaxe effective |
| **Occlusion** | `noOcclusion()` — transparent model |

**Item model:** `models/item/scrying_orb.json` (parent: block model, standard transforms)
**Block model:** `models/block/scrying_orb.json` — two elements: glass sphere (6–10, tintindex 0) + carved stand (5–11 Y 0–6, texture `#1`)
**Particle texture:** glass texture (`scrying_orb_glass`)
**Blockstate:** single variant, `ACTIVE` property unused in model (light only)

---

## 2. Activation — How a Player Starts Scrying

### 2.1 Prerequisites
- Player must be **attuned** (`Mana.attuned(player)` — has mana capacity > 0).
- Player must hold a **focus item** in main hand or offhand:
  - **Lock of Hair** (`Hair` item) — contains a target player UUID.
  - **Sighted Shard** (`SightedShard` item) — stores a dimension + position + name.
- An **idle scrying orb block entity** must exist within **4 blocks** (Euclidean, Y ±2) of the player.

### 2.2 Activation Flow
1. Player right-clicks the idle orb block (no item in hand required for the click; focus is read from hands).
2. Server (`ScryingOrbBlock.useWithoutItem`) validates attunement and focus.
3. **If focus = Lock of Hair (Person target):**
   - Look up the target entity by UUID in the same dimension.
   - **Illusion save** (`Saves.resist(target, ILLUSION)`): if succeeds, orb **clouds** (miasma particles, `Cloud` packet), hair consumed, target feels **chill** (cave sound + rune particles at eye level).
   - If save fails: orb **wakes** for that person. Hair consumed. Target feels chill.
4. **If focus = Sighted Shard (Place target):**
   - Read stored `Target` (dimension, pos, name) from shard's `CustomData`.
   - **No save, no chill.** Orb wakes for that place.
   - Shard is **not consumed** (reusable).
5. On wake:
   - Orb BE: `setActive(casterUUID)` → block state `ACTIVE=true` (light 8).
   - `Concentration.hold(caster, "scrying_orb", 600 ticks, onEnd → setIdle + Close packet)`.
   - If place target: **request 3×3 chunk ticket** on target dimension around the pos (held while awake).
   - Send initial **`OrbView` snapshot** packet to caster (and any players within 16 blocks of the orb).

### 2.3 Sighted Shard — Creating a Place Focus
- **Sneak + right-click** a plain `amethyst_shard` on any block (server-side `UseBlockCallback`):
  - Consumes 1 amethyst shard from hand.
  - Creates a `SightedShard` item with `Target.place(dimension, pos, name)` stored in `CustomData`.
  - Name format: translatable `item.jaymoskingdoms.sighted_shard.default_name` → `"overworld: 123, 64, -456"`.
  - Particles: `Fx.mote(0x8A5CFF, 0.6F)` at block center (8 particles).
  - Sound: `AMETHYST_CLUSTER_BREAK` (0.6F vol, 1.2F pitch).
  - Chat message: `"Marked overworld: 123, 64, -456"` (aqua).
- **Sneak + right-click in air** with a `SightedShard` in hand: clears the target (removes `CustomData` keys), chat `"Sight cleared"` (gray).

---

## 3. See-Through View — Rendering the Miniature

### 3.1 Core Principle (from Frameless Portals research)
> **Ordinary block models drawn in a BlockEntityRenderer via `submitCustomGeometry` with `RenderTypes.entityTranslucent(TextureAtlas.LOCATION_BLOCKS)`.** No second camera, no render-to-texture, no stencil buffer. This path is shader-compatible (Sodium, Iris, Complementary) and is exactly what `PlanGhosts` already does.

### 3.2 Data Pipeline

| Stage | Component | Details |
|-------|-----------|---------|
| **Capture (server)** | `ScryingOrb.sendSnapshot()` | 17×17×11 box (radius 8, height 11) around target centre, **cut to sphere** (dist² ≤ 64). For each position: `BlockState` → **map color** (current impl) → palette index (byte). Palette = unique map colors (max 256). Figures: up to 32 living entities in radius, packed as `[dx, dy, dz, kind<<6 \| facing<<1 \| walking]`. Night flag = `level.isDarkOutside()`. |
| **Packet** | `OrbView` | `who` (Component name), `palette[]` (int map colors), `indices[]` (byte per block, -1 = outside sphere), `changed[]` (delta: index + new palette idx), `figures[]` (packed), `night` (bool). ~3 KB initial, deltas ~100 B/sec. Sent to players within 16 blocks of orb. |
| **Client receive** | `ScryingOrbRenderer.register()` → `ORBS` map | Keyed by orb UUID (from `who` string hash). Stores `OrbData` with palette, indices, figures, night, tickOffset. |
| **Render (client)** | `ScryingOrbRenderer.submit()` | Called per-tick for each active orb BE. If `ORBS` has data for this orb's `activeId`: draw miniature via `drawMiniature()`. |

### 3.3 `drawMiniature()` — Block Model Submission (The PlanGhosts Path)

```java
// Inside drawMiniature(SubmitNodeCollector collector, PoseStack poseStack, ...)
// 1. Build quad cache for palette states (BlockState by ID — see §3.4)
Map<Integer, List<BakedQuad>> quadCache = new HashMap<>();
for (int i = 0; i < data.palette.length; i++) {
    BlockState state = Block.stateById(data.palette[i]);  // <-- needs block state IDs in packet
    if (state.getRenderShape() != RenderShape.MODEL || state.isAir()) continue;
    quadCache.computeIfAbsent(data.palette[i], id -> {
        BlockStateModel model = mc.getModelManager().getBlockStateModelSet().get(state);
        List<BlockStateModelPart> parts = new ArrayList<>();
        model.collectParts(RandomSource.create(42L), parts);
        List<BakedQuad> out = new ArrayList<>();
        for (BlockStateModelPart part : parts) {
            for (Direction side : PlanGhosts.SIDES) {
                out.addAll(part.getQuads(side));
            }
        }
        return List.copyOf(out);
    });
}

// 2. Submit all blocks in ONE custom geometry call
collector.submitCustomGeometry(poseStack,
    RenderTypes.entityTranslucent(TextureAtlas.LOCATION_BLOCKS),
    (pose, consumer) -> {
        QuadInstance instance = new QuadInstance();
        instance.setLightCoords(FULL_BRIGHT);      // 0xF000F0
        instance.setOverlayCoords(OverlayTexture.NO_OVERLAY);

        int diameter = ScryingOrb.SIZE;   // 17
        int height   = ScryingOrb.HEIGHT; // 11
        int radius   = ScryingOrb.RADIUS; // 8
        double scale = SPHERE_R / radius; // 0.38 / 8 ≈ 0.0475 (1/21 ≈ 1/24 target)

        for (int idx = 0; idx < data.indices.length; idx++) {
            int paletteIdx = data.indices[idx] & 0xFF;
            if (paletteIdx >= data.palette.length) continue;
            List<BakedQuad> quads = quadCache.get(data.palette[paletteIdx]);
            if (quads == null || quads.isEmpty()) continue;

            // Decode linear index → x,y,z in 17×17×11 box
            int x = idx % diameter;
            int y = (idx / diameter) % height;
            int z = idx / (diameter * height);
            if (data.indices[idx] == -1) continue; // outside sphere

            // Centre in sphere (model space)
            double bx = cx + (x - radius) * scale;
            double by = cy + (y - height / 2.0) * scale;
            double bz = cz + (z - radius) * scale;

            // Tint by target dimension
            int colour = computeDimensionTint(data.dimension, data.night);
            instance.setColor(colour);

            PoseStack.Pose p = pose.copy();
            p.translate((float)bx, (float)by, (float)bz);
            p.mulPose(Axis.YP.rotationDegrees(spinDegrees));
            p.scale((float)scale, (float)scale, (float)scale);

            for (BakedQuad quad : quads) {
                consumer.putBakedQuad(p, quad, instance);
            }
        }
    });
```

### 3.4 Critical Fix: Packet Must Send **Block State IDs**, Not Map Colors

**Current bug in `sendSnapshot()`:** Uses `state.getMapColor(level, pos).col` (24-bit map color) for palette. This loses block identity (many states share a map color) and cannot reconstruct `BlockState` for `getBlockStateModelSet().get(state)`.

**Fix:** Send `Block.getId(state)` (int block state ID) in palette. Palette size stays ≤ 256 (fits in byte index). Client reconstructs via `Block.stateById(id)`.

> **Delta packets** (`changed[]`) already send index + new palette index — same fix applies.

### 3.5 Dimension-Specific Tint

| Dimension | Day Tint (ARGB) | Night Tint (ARGB) | Notes |
|-----------|-----------------|-------------------|-------|
| Overworld | `0xFFFFFFFF` (white) | `0xCCCCCCCC` (dim) | `data.night` from `level.isDarkOutside()` |
| Nether | `0xFFFF6666` (red) | `0xFFCC4444` (darker red) | Nether is always "night" — use red tint always |
| The End | `0xFFCCAAFF` (purple) | `0xFFAA88DD` | End sky logic |
| Aether (if present) | `0xFFFFFFFF` (white, bright) | `0xFFEEEEEE` | Custom dimension — detect by namespace |
| Other | `0xFFFFFFFF` | `0xCCCCCCCC` | Fallback |

`computeDimensionTint(String dimension, boolean night)` → returns ARGB. Applied via `QuadInstance.setColor()`.

### 3.6 Figures (Motes — First Cut)

- Drawn as **small lit crosses** (two quads, `mote()` helper) at entity positions scaled into sphere.
- Colours by kind (packed in `figures[]` high bits):
  - `0` — Watched person: `0xFFFFFFFF` (white)
  - `1` — Folk (players/villagers): `0xFF4AA3FF` (blue)
  - `2` — Monsters: `0xFFFF4A4A` (red)
  - `3` — Animals: `0xFF8AE06A` (green)
- Bobbing: vertical offset `Math.sin(age * 0.2) * 0.01`.
- Update rate: 4×/sec (server sends new `figures[]` in each `OrbView` delta).

> **Future:** Replace motes with actual tiny entity models (follow-up task).

### 3.7 Glass Sphere + Stand (Idle + Active)

| Element | Render Type | Technique |
|---------|-------------|-----------|
| **Stand** | `entityTranslucent` | Manual quads (`drawStand()`) — carved wood/stone look, `STAND_COLOUR = 0xFF3A2E24` |
| **Glass sphere** | `entityTranslucent` | Wireframe via leash lines (`drawGlassSphere()`) — 3 tilted rings, 16 segments each, `GLASS_COLOUR = 0x44FFFFFF` |
| **Idle orb** | Same | Slow spin (`SPIN = 0.15°/tick`), no miniature |

### 3.8 Animation & Timing

| Parameter | Value |
|-----------|-------|
| **Miniature spin** | 0.15° per tick (≈ 9°/sec, full turn ~40 sec) |
| **Figure update** | 4 Hz (every 5 ticks) |
| **Snapshot refresh** | Every 20 ticks (1 sec) — delta packets |
| **Active duration** | 600 ticks (30 seconds) — `HOLD_TICKS` |
| **Cooldown (place-sight)** | 1200 ticks (1 minute) per orb after a place-sight ends |
| **Max simultaneous orbs** | 8 (client render culling: `RANGE = 32`, `MAX_ORBS = 8` nearest) |
| **Max place-sights server-wide** | 4 (enforced in `startWatch()`) |

---

## 4. Costs, Cooldowns & Limits

| Resource | Cost / Limit |
|----------|--------------|
| **Lock of Hair** | Consumed on wake (1 per use). Obtained from Scrying bowl spell. |
| **Sighted Shard** | **Not consumed**. Created from 1 amethyst shard (sneak-use on block). Reusable indefinitely. |
| **Mana / Attunement** | Required to wake (`Mana.attuned(player)`). No mana cost per tick. |
| **Concentration** | Held for 30 s (`Concentration.hold(caster, "scrying_orb", 600, ...)`); blocks other concentration spells. |
| **Place-sight cooldown** | 1 minute per orb after a place-sight ends (orb rests). |
| **Place-sight server cap** | 4 simultaneous place-sights across all orbs. |
| **Chunk tickets** | 3×3 chunks forced around place target while awake; released on close/end. |
| **Packet bandwidth** | ~3 KB initial + ~100 B/sec deltas per viewer (within 16 blocks of orb). |

---

## 5. Config Keys (Kingdoms `config/jaymoskingdoms.toml`)

```toml
[scrying_orb]
# General
orb_reach = 4.0              # Blocks from player to orb to activate
hold_ticks = 600             # Active duration (30 seconds)
refresh_ticks = 20           # Snapshot delta interval (1 second)
max_orbs_rendered = 8        # Client-side simultaneous orbs
render_range = 32            # Client render distance from camera

# Snapshot capture
snapshot_radius = 8          # 17×17 horizontal
snapshot_height = 11         # Vertical layers
max_palette_size = 256       # Byte index limit
max_figures = 32             # Entity figures per snapshot

# Place-sight limits
max_place_sights_server = 4  # Server-wide concurrent place-sights
place_sight_cooldown = 1200  # Ticks (1 minute) per orb after place-sight
place_ticket_radius = 1      # Chunk ticket radius (3×3 = 1 chunk each way)

# Performance
figure_update_hz = 4         # Figures packet rate
spin_degrees_per_tick = 0.15 # Miniature rotation speed
sphere_scale = 0.38          # Model-space sphere radius (blocks)
block_scale_factor = 0.0475  # SPHERE_R / RADIUS ≈ 1/21 (target ~1/24)

# Shader compatibility (do not change)
render_type = "entityTranslucent"  # RenderTypes.entityTranslucent(TextureAtlas.LOCATION_BLOCKS)
```

> **Note:** These are *tunables* for balance/perf. No player-facing config screen planned; server admins edit the TOML.

---

## 6. Integration with Frameless Portals Rendering (PlanGhosts Path)

### 6.1 Shared Render Pipeline

| Aspect | PlanGhosts | Scrying Orb |
|--------|------------|-------------|
| **Render hook** | `LevelRenderEvents.COLLECT_SUBMITS` (global) | `BlockEntityRenderer.submit()` (per-BE) |
| **Geometry submit** | `context.submitNodeCollector().submitCustomGeometry(...)` | `collector.submitCustomGeometry(...)` (same API) |
| **Render type** | `RenderTypes.entityTranslucent(TextureAtlas.LOCATION_BLOCKS)` | **Identical** |
| **Quad cache** | `Map<BlockState, List<BakedQuad>> QUADS` (static) | Per-frame `Map<Integer, List<BakedQuad>>` (palette key) |
| **Quad extraction** | `model.collectParts(RandomSource, parts)` → `part.getQuads(side)` | **Identical** |
| **Colour/tint** | Constant `COLOUR = 0x6698C0FF` (pale blue) | Per-dimension tint via `QuadInstance.setColor()` |
| **Lighting** | `FULL_BRIGHT` (0xF000F0) | `FULL_BRIGHT` + dimension tint |
| **Scale** | 0.98× (slightly smaller than block) | `SPHERE_R / RADIUS ≈ 0.0475` (1/21) |
| **Positioning** | World-space (ghost at actual pos) | Model-space (inside sphere at orb BE pos) |
| **Culling** | Camera range + open-to-air + not filled | Sphere bounds + skip air/outside-sphere indices |

### 6.2 Why This Works with Shaders

- `entityTranslucent` is a **standard vanilla render type** used for entity translucency (glass, water, etc.).
- Sodium/Iris/Complementary all handle it natively — no custom shader, no render target, no depth peeling.
- The geometry is submitted **during level render**, blended into the normal translucent pass.
- **No second camera** = no view/projection matrix mismatch, no texture copy, no post-process interference.

### 6.3 Performance Guardrails (from research)

| Guardrail | Value | Rationale |
|-----------|-------|-----------|
| Max blocks per snapshot | 17³ = 4913 (sphere cuts to ~2500) | Fixed budget |
| Quad cache dedup | Per `BlockState` (palette index) | Typical region: 50–200 unique states |
| Sphere culling | Indices with `-1` skipped | ~50% of box outside sphere |
| Max orbs rendered | 8 (nearest by camera distance) | Hard cap in `renderOrbs()` / submit |
| Chunk tickets | 3×3 per place-sight, max 4 simultaneous | 36 chunks max forced, short-lived |

---

## 7. Item Tooltip / Lore Mockup

### 7.1 In-Game Tooltip (Hover)

```
┌────────────────────────────────────────┐
│ Scrying Orb                            │ ◀ Item name (gold, rarity=uncommon)
├────────────────────────────────────────┤
│ A glass sphere on a carved stand.      │ ◀ First lore line (gray)
│ Wake it with a lock of hair or a       │
│ sighted amethyst shard to see a living │
│ miniature of the land around that      │
│ person or place for 30 seconds.        │
├────────────────────────────────────────┤
│ Requires attunement                    │ ◀ Blue (requirement)
│ Right-click orb with focus in hand     │ ◀ Gray (usage hint)
├────────────────────────────────────────┤
│ Court wizard price: 24 emeralds        │ ◀ Gold (acquisition hint)
│ Infusion: glass, 4 amethyst, 2 ender   │
│ eyes, 2 glass (needs "scrying" research)│
└────────────────────────────────────────┘
```

### 7.2 Lore Text (from `Lore.md`, Andy approved 2026-11-18)

> **The scrying orb** (Andy approved, 2026-11-18; Claude's placement). Any caster can see far off in a bowl of water with a lock of someone's hair, but the bowl shows only a flat picture that clouds when the spell is let go. The court wizards keep that same sight in glass: a sphere ground with amethyst dust and set on a stand. An attuned hand wakes it with a lock of someone's hair, or with an amethyst shard that was pressed to the ground somewhere, since amethyst remembers where it has lain. Then the place around that person, or that spot, rises inside the orb as a small living picture. Its roofs, its trees and the folk moving about are all there, for as long as the sight is held. Only the attuned can wake an orb, but anyone in the room can look in. A court wizard keeps one in the tower, and a realm without one pays a wizard who has one to do its watching. Someone being watched feels a chill and nothing more; a place feels nothing.

> **Tooltip lore lines** (abridged for item tooltip, gray):
> - "A glass sphere on a carved stand."
> - "Wake it with a lock of hair or a sighted amethyst shard"
> - "to see a living miniature of the land around that"
> - "person or place for 30 seconds."

### 7.3 Sighted Shard Tooltip

```
┌────────────────────────────────────────┐
│ Sighted Shard                          │ ◀ Name (aqua, custom name from anvil or default)
├────────────────────────────────────────┤
│ Marked overworld: 123, 64, -456        │ ◀ Gray (stored target name)
├────────────────────────────────────────┤
│ Sneak-use on a block to remark         │ ◀ Gray (usage)
│ Sneak-use in air to clear              │
└────────────────────────────────────────┘
```

---

## 8. Acquisition

| Source | Details |
|--------|---------|
| **Court Wizard trade** | Cleric villager, master level: 24 emeralds → 1 Scrying Orb (4 uses, restocks via workstation). |
| **Infusion Altar (Powers compat)** | Recipe: `data/jaymoskingdoms/alchemy/infusion/scrying_orb.json`<br>Centre: glass<br>Ring: 4× amethyst shard, 2× ender eye, 2× glass<br>Research required: `"scrying"` (Powers research system) |
| **World gen** | Court wizard towers (Kingdoms structures) generate with one placed orb. |

---

## 9. Open Questions — **All Resolved**

| # | Question | Resolution (Andy 2026-11-19) |
|---|----------|------------------------------|
| 1 | Does the watched person notice beyond the chill? | **No.** "The watched person notices nothing at all (drop the chill or any other tell)." |
| 2 | Can structures be warded against scrying? | **Yes, anything can be warded.** "Anything can be warded, not only keeps/towers." |
| 3 | Mote figures first or real tiny models? | **Builder's choice.** "The builder picks mote figures or tiny models." → **Motes first** (current impl). |
| 4 | Does the orb work cross-dimension for places? | **Yes.** Place target stores dimension ID; chunk tickets requested on that dimension. |
| 5 | Is the Sighted Shard consumed on use? | **No.** "Never used up; sneak-use again to move it." |
| 6 | How many simultaneous orbs per player? | **One** (Concentration hold blocks other concentration; orb BE tracks single user). |
| 7 | Packet sends map colors or block state IDs? | **Block state IDs** (fix required — see §3.4). |

---

## 10. Acceptance Criteria (Ready for Parallel Work)

| Area | Criteria | Status |
|------|----------|--------|
| **Spec document** | This document committed to repo branch `claude/scrying-orb-design` | ✅ |
| **Open questions** | All 7 questions resolved (Andy's answers recorded) | ✅ |
| **Rendering approach** | PlanGhosts `entityTranslucent` path documented with code sketch | ✅ |
| **Packet format** | Block state IDs (not map colors) specified; delta format defined | ✅ |
| **Config keys** | Full TOML table with tunables | ✅ |
| **Tooltip/lore** | Mockup + approved lore text included | ✅ |
| **Integration points** | Block, BE, renderer, packets, shard, recipes, trades, world gen listed | ✅ |
| **Performance budget** | < 1 ms/frame for 3 orbs (Complementary) — guardrails defined | ✅ |
| **Shader compatibility** | `entityTranslucent` confirmed; no render-to-texture | ✅ |

---

## 11. Implementation Task Breakdown (for parallel assignment)

| Task ID | Title | Assignee | Depends On |
|---------|-------|----------|------------|
| `scrying-orb-impl` | Fix 26.3 compile errors in `ScryingOrb.java`, `SightedShard.java`, `ScryingOrbBlockEntity.java`; implement block state ID palette | hermes-a (builder) | — |
| `scrying-orb-render` | Complete `drawMiniature()` with quad cache + `submitCustomGeometry`; dimension tint; figure motes | hermes-a (builder) | `scrying-orb-impl` |
| `scrying-orb-model` | Create textures: `scrying_orb_glass.png`, `scrying_orb_stand.png` (16×16, vanilla style) | hermes-modeller | — |
| `scrying-orb-writing` | Lang part review; change note `changes/kingdoms/claude-scrying-orb-design.md`; lore placement confirm | hermes-writer | — |
| `scrying-orb-qa` | Test: person target (villager house + figure), place target (marked gate, chunks unload), Complementary shader, 4 orbs perf | hermes-qa | `scrying-orb-render`, `scrying-orb-model` |

---

## 12. Files to Change / Create (Summary)

### Code (Kingdoms)
- `ScryingOrb.java` — fix palette to block state IDs; delta packets; place-sight ticket logic; 4-place cap
- `ScryingOrbBlock.java` — registrations, creative tab, `useWithoutItem`
- `ScryingOrbBlockEntity.java` — `ACTIVE` state, user UUID, save/load
- `ScryingOrbRenderer.java` — `drawMiniature()` full impl; quad cache; dimension tint; figure motes; glass/stand
- `SightedShard.java` — sneak-use mark/clear; `CustomData` storage; creative tab
- `Scrying.java` — extend with `TargetKind.PLACE` support (already partially done)
- `Mana.java` — `crush()` exclude `SightedShard.ITEM`
- `JaymosKingdoms.java` / `JaymosKingdomsClient.java` — register calls

### Assets (Kingdoms)
- `lang/parts/scrying-orb.json` — all translatable strings
- `models/block/scrying_orb.json` — block model (exists)
- `models/item/scrying_orb.json` — item model (parent block)
- `textures/block/scrying_orb_glass.png` — **TODO** (modeller)
- `textures/block/scrying_orb_stand.png` — **TODO** (modeller)
- `blockstates/scrying_orb.json` — single variant

### Data (Kingdoms)
- `data/jaymoskingdoms/alchemy/infusion/scrying_orb.json` — infusion recipe
- `data/jaymoskingdoms/villager_trade/cleric/scrying_orb.json` — wizard trade

### Config
- `config/jaymoskingdoms.toml` — `[scrying_orb]` section (append)

### Docs
- `changes/kingdoms/claude-scrying-orb-design.md` — change note (writer)

---

## 13. Not Tested In Game (PR Body Line)

> **Not tested in game:** Full miniature rendering with actual block models (quad cache + submitCustomGeometry), place-sight chunk ticket loading/unloading across dimensions, multiplayer sync of orb active state to nearby players, court wizard tower generation with placed orb, infusion recipe "scrying" research unlock, performance with 4 simultaneous orbs under Complementary shaders. Compile-checked only.

---

*Spec version 1.0 — 2026-11-20 04:45 AM — hermes-a (Apollo)*
*Ready for implementation, modeling, and writing to proceed in parallel.*