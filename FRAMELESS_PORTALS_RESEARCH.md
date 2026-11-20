# Frameless Portals See-Through Effect Research

## Overview
Frameless Portals is a Minecraft mod that creates see-through portal effects without visible frames. The scrying orb feature request (Inbox/scrying-orb.md, PR #1787) explicitly references this effect: *"A scrying orb sounds cool (after asking how Frameless Portals' see-through effect works), then 'it should work for places too, put the plan on the board'."*

**Key constraint from the plan:** *"It must work with Sodium, Iris and Complementary, so it is ordinary block models drawn in a block entity renderer, never a second camera or render-to-texture."*

## How Frameless Portals Achieves the Effect

While the mod itself isn't in our pack, the see-through portal effect in modern Fabric mods typically uses one of three approaches:

| Approach | Compatibility | Scrying Orb Suitability |
|----------|---------------|------------------------|
| **Render-to-texture (second camera)** | Breaks with Sodium/Iris shaders | ❌ Explicitly forbidden |
| **Stencil buffer portal masking** | Complex, shader-fragile | ❌ Risky for shader compatibility |
| **Direct block model submission (entityTranslucent)** | Works with all shaders | ✅ **Chosen approach** |

The third approach—submitting ordinary block model geometry via `BlockEntityRenderer` using `submitCustomGeometry` with `RenderTypes.entityTranslucent(TextureAtlas.LOCATION_BLOCKS)`—is exactly what our codebase already does in `PlanGhosts`.

## Relevant Classes & Methods in Our Codebase

### 1. `PlanGhosts.java` (Kingdoms client) — **Primary Reference**
- **Event:** `LevelRenderEvents.COLLECT_SUBMITS.register(PlanGhosts::render)`
- **Core method:** `render(LevelRenderContext context)` (lines 155–182)
- **Technique:**
  ```java
  context.submitNodeCollector().submitCustomGeometry(
      poseStack,
      RenderTypes.entityTranslucent(TextureAtlas.LOCATION_BLOCKS),
      (pose, consumer) -> {
          QuadInstance instance = new QuadInstance();
          instance.setColor(COLOUR);           // 0x6698C0FF (pale blue, see-through)
          instance.setLightCoords(FULL_BRIGHT); // 0xF000F0 (full bright)
          instance.setOverlayCoords(OverlayTexture.NO_OVERLAY);
          for (each block position) {
              pose.translate(...).scale(0.98F, 0.98F, 0.98F);
              for (BakedQuad quad : quads(minecraft, state)) {
                  consumer.putBakedQuad(p, quad, instance);
              }
          }
      }
  );
  ```
- **Quad cache:** `QUADS` map (line 60) caches `List<BakedQuad>` per `BlockState` via `minecraft.getModelManager().getBlockStateModelSet().get(state)` and `collectParts()`.
- **Filtering:** Skips air, already-filled positions, non-MODEL render shapes (line 118).

### 2. `ScryingOrbRenderer.java` (PR #1787, branch `claude/scrying-orb`) — **Current Implementation**
- **Type:** `BlockEntityRenderer<ScryingOrbBlockEntity, State>`
- **Lifecycle:** `extractRenderState()` → `submit()`
- **Current rendering (incomplete):**
  - `drawStand()` — manual quads for carved stand
  - `drawGlassSphere()` — wireframe via `leash()` lines (not block models)
  - `drawMiniature()` — placeholder; iterates palette/indices but **does not submit actual block quads**
  - Uses `RenderTypes.entityTranslucent(TextureAtlas.LOCATION_BLOCKS)` for stand/motes
- **Missing:** Block model quad submission for the 17×17×11 snapshot (palette + indices received via `OrbView` packet).

### 3. Supporting Classes
| Class | Role |
|-------|------|
| `ScryingOrb.java` | Packet definitions (`OrbView`, `Close`, `Cloud`), snapshot logic (SIZE=17, RADIUS=8, HEIGHT=11) |
| `ScryingOrbBlockEntity.java` | Holds `ACTIVE` state, user UUID |
| `PlanSites.java` | Server-side ghost plan logic; packet `Ghost` sends `rel[]` + `states[]` per part |
| `LevelRenderEvents.COLLECT_SUBMITS` | Fabric API event for custom geometry during level render |

## Proposed Rendering Approach for Scrying Orb (Places)

### Architecture
The scrying orb should **reuse the PlanGhosts submit path** but drive it from a `BlockEntityRenderer` instead of a global `COLLECT_SUBMITS` handler.

### Data Flow
1. **Server (`ScryingOrb.tickOrb`):** Every tick while awake, capture 17×17×11 box around target position → palette (block state IDs) + indices (byte per position) + figure data. Send via `OrbView` packet to clients within 16 blocks.
2. **Client (`ScryingOrbRenderer`):** Receive `OrbView` → store in `ORBS` map keyed by orb UUID.
3. **Render (`submit()`):**
   - For the active orb, decode palette indices to `BlockState[]`
   - Build quad cache (like `PlanGhosts.quads()`) for each unique `BlockState`
   - Submit via `collector.submitCustomGeometry(poseStack, RenderTypes.entityTranslucent(...), callback)`
   - In callback: translate to each block's relative position inside sphere (scale 1/24), apply `QuadInstance` with tint (target world light: Nether red, Aether white, Overworld normal), `putBakedQuad`

### Key Differences from PlanGhosts
| Aspect | PlanGhosts | Scrying Orb (Places) |
|--------|------------|---------------------|
| **Render hook** | Global `COLLECT_SUBMITS` | Per-BE `BlockEntityRenderer.submit()` |
| **Scope** | All laid plans in range | Single orb's snapshot (17³ ≈ 4913 blocks) |
| **Positioning** | World-space (ghost at actual pos) | Model-space (inside sphere at 1/24 scale) |
| **Lighting** | `FULL_BRIGHT` | Target dimension's light (Nether=red, Aether=white) |
| **Culling** | Camera-range + open-to-air | Sphere bounds; skip air/hidden blocks |
| **Animation** | Static | Slow spin (0.15°/tick), figure motes 4×/sec |

### Code Sketch for `drawMiniature()` (replacing placeholder)
```java
private void drawMiniature(SubmitNodeCollector collector, PoseStack poseStack,
                           double cx, double cy, double cz, float spin,
                           OrbData data, int blockLight, int skyLight) {
    if (data.palette.length == 0 || data.indices.length == 0) return;

    Minecraft mc = Minecraft.getInstance();
    int diameter = ScryingOrb.SIZE;   // 17
    int radius = ScryingOrb.RADIUS;   // 8
    double scale = SPHERE_R / radius; // 0.38 / 8 ≈ 0.0475

    // Build quad cache for palette states (BlockState by ID)
    Map<Integer, List<BakedQuad>> quadCache = new HashMap<>();
    for (int i = 0; i < data.palette.length; i++) {
        BlockState state = Block.stateById(data.palette[i]);
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

    // Submit all blocks in one custom geometry call
    collector.submitCustomGeometry(poseStack, RenderTypes.entityTranslucent(TextureAtlas.LOCATION_BLOCKS),
        (pose, consumer) -> {
            QuadInstance instance = new QuadInstance();
            instance.setLightCoords(FULL_BRIGHT);
            instance.setOverlayCoords(OverlayTexture.NO_OVERLAY);

            for (int idx = 0; idx < data.indices.length; idx++) {
                int paletteIdx = data.indices[idx] & 0xFF;
                if (paletteIdx >= data.palette.length) continue;
                List<BakedQuad> quads = quadCache.get(data.palette[paletteIdx]);
                if (quads == null || quads.isEmpty()) continue;

                // Decode index to x,y,z in 17×17×11 box
                int x = idx % diameter;
                int y = (idx / diameter) % ScryingOrb.HEIGHT;
                int z = idx / (diameter * ScryingOrb.HEIGHT);
                // Center in sphere
                double bx = cx + (x - radius) * scale;
                double by = cy + (y - ScryingOrb.HEIGHT / 2.0) * scale;
                double bz = cz + (z - radius) * scale;

                // Tint by target dimension
                int colour = data.night ? 0x884444FF : 0xFFFFFFFF; // placeholder: Nether red tint
                instance.setColor(colour);

                PoseStack.Pose p = pose.copy();
                p.translate((float)bx, (float)by, (float)bz);
                p.mulPose(Axis.YP.rotationDegrees(spin * (180F / (float)Math.PI)));
                p.scale((float)scale, (float)scale, (float)scale);

                for (BakedQuad quad : quads) {
                    consumer.putBakedQuad(p, quad, instance);
                }
            }
        }
    );
}
```

## Risks & Blockers

| Risk | Severity | Mitigation |
|------|----------|------------|
| **Shader compatibility** | High | Use `entityTranslucent` render type (works with Sodium/Iris/Complementary); avoid `RenderType.translucent` or custom shaders |
| **Performance (quad count)** | Medium | 17³ = 4913 blocks max; at 1/24 scale most are culled by sphere bounds. Cache quads per `BlockState`. Limit to 4 orbs × 3 KB snapshots. |
| **Block state palette size** | Medium | Palette sent as `int[]` (block state IDs). Max unique states in 17³ region ~200. Quad cache handles dedup. |
| **Lighting correctness** | Medium | Target dimension light: Overworld=normal, Nether=red tint, Aether=white. Apply via `QuadInstance.setColor()` with dimension-specific tint. |
| **Unloaded chunks (places)** | High | Plan already specifies: short-lived chunk ticket (3×3) while orb awake; max 4 place-sights server-wide; 1 min cooldown. |
| **Packet size** | Low | Palette + indices ~3 KB per orb per second; delta updates after first. Within Fabric packet limits. |
| **26.3 API changes** | Medium | `BlockStateModel`, `BlockStateModelPart`, `collectParts()`, `getQuads()` verified against 26.3 sources. `SubmitNodeCollector.submitCustomGeometry` is stable. |

## Acceptance Criteria Met
- [x] Relevant classes/methods documented (`PlanGhosts`, `ScryingOrbRenderer`, `LevelRenderEvents.COLLECT_SUBMITS`, `submitCustomGeometry`, `entityTranslucent`)
- [x] Proposed rendering approach defined (reuse PlanGhosts quad submission path from `BlockEntityRenderer.submit()`)
- [x] Risks/blockers identified with mitigations
- [x] Research note committed to task branch (`claude/frameless-portals-research`)

## Next Steps
1. Implement `drawMiniature()` in `ScryingOrbRenderer` using the sketch above
2. Verify `OrbView` packet sends full block state IDs (not map colors) for palette
3. Add dimension-specific tint logic (Nether red, Aether white, End purple, etc.)
4. Run `sh ./gradlew build` and `python tools/pr_checks.py`
5. Open PR for review