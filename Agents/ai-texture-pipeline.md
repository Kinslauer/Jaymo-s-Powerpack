---
status: stalled
task: AI-assisted texture generation pipeline research
branch: prometheus
with: 
pr: 
mods: 
versions: 
started: 2026-11-21
owns:
---
# AI texture pipeline for item PNGs and model textures

**Task:** Research how to apply an AI-assisted texture generation pipeline (Fable 5.2 + Nano Banana Pro + Meshy API + Blender + Unity/URP style) to our item PNGs and model textures for Powers/Kingdoms/Kitchen. Determine which tools fit our stack (Minecraft 26.3 Fabric, GeckoLib models, Blockbench .bbmodel), what the cost would be, and what a reusable workflow looks like for generating item textures and model textures in our pack's style.

**Plan:**
1. Research the three Reddit-referenced tools (Fable 5.2, Nano Banana Pro, Meshy API) and any alternatives suitable for Minecraft 26.3 Fabric/GeckoLib/Blockbench pipelines.
2. Assess pipeline feasibility: generation → Blockbench .bbmodel → GeckoLib export → item model JSON.
3. Evaluate costs (API keys, subscription models) against our budget.
4. Map out a reusable end-to-end workflow.
5. Assess whether generated output fits our pack's existing art style (browns/greys for Powers, specific colour palettes).
6. Produce a process recommendation document with tools, costs, steps, and fit assessment.

**Notes:** Research/investigation first — no code or art changes yet. The output should be a process recommendation with tools, costs, and steps, plus an assessment of whether it fits our pack's art style and pipeline.

**Done:** Research complete (2026-11-21).

## Research Analysis (completed)

### Tools Evaluated

**1. Fable 5.2 (Anthropic/Claude)**
- **What it does**: "Paints" images using simulated brushstrokes via 1,000-2,000 line Python scripts; generates 3D worlds/procedural scenes, not individual 2D textures
- **Output**: Three.js browser-based rendering, procedural 3D geometry + shader-based materials
- **Minecraft suitability**: ❌ Not suitable — designed for 3D world generation, not 16x16 pixel art item textures. Would require complete re-architecture to produce usable item PNGs.
- **Cost**: ~8 hours agent time on Claude Max subscription (~$20-30/month)
- **Key limitation**: Output is procedural 3D/scene generation, not the pixel art texture format Minecraft uses

**2. Nano Banana Pro (Google DeepMind/Gemini 3 Pro Image)**
- **What it does**: Image generation and editing model; native 2K resolution; text rendering; localized editing; color grading; can blend up to 14 reference images
- **Output**: PNG/JPEG images at 2K (2048×2048) natively; 4K with upscaling
- **Minecraft suitability**: ⚠️ Partially suitable — can generate base textures but requires extensive post-processing to convert from realistic rendering to Minecraft pixel art style (color palette reduction, pixelation, lighting adjustment). Our existing art uses specific browns/greys (e.g., archer cloak averages `#442c1e`) that would need manual matching.
- **Cost**: Part of Gemini Pro API usage; variable per-image cost. Google AI Studio has free tier with quotas, production usage billed by token/consumption.
- **Key capability**: Best text rendering of the three models; can follow creative direction and maintain consistency across generated images

**3. Meshy API (AI 3D model + texture generation)**
- **What it does**: Text-to-3D or image-to-3D with optional texturing; outputs textured GLB models with UV maps
- **Output**: Textured 3D mesh (optionally in GLB format) + extractable texture PNGs
- **Minecraft suitability**: ✅ Most suitable — generates textured 3D models directly compatible with our extraction pipeline. Can generate low-poly meshes that resemble Minecraft styles when prompted appropriately.
- **Cost**: Pro plan $20/month for 1,000 credits. Full generation (mesh + 2K texture) = 30 credits ≈ $0.60 per generation. Free plan: 100 credits/month ≈ 3 generations. Retexture-only: 10 credits ≈ $0.20.
- **Key capability**: Smart Topology mode (5 credits) for lower-detail but much cheaper generation; standard mode gives high-detail meshes

### Recommended Pipeline

Given our Minecraft 26.3 Fabric / GeckoLib / Blockbench pipeline and existing art style (brown/grey palettes, specific colour references), the recommended approach is:

**Primary: Meshy API + post-processing**
1. **Generate base model + texture**: Use Meshy Text-to-3D or Image-to-3D with a prompt describing the item (e.g., "copper key, Minecraft item style, brown metal, 16x16 pixels"). Use Smart Topology mode (5 credits) for quick prototypes or standard mode (20+10=30 credits) for higher detail.
2. **Extract texture PNG**: Pull the generated texture from the GLB output.
3. **Post-process to pack style**:
   - Reduce color palette to match existing pack colours (our browns average `#442c1e`, greys, etc.)
   - Apply Minecraft pixel art conventions: hard pixels, 3-5 shades per material, light from top-left, one-pixel darker edges where parts meet
   - Use art-agent `paint_texture` tool for touch-ups
4. **Build Blockbench .bbmodel**: Use our existing `build_model` tool with proper box-UV mapping; the extracted texture's UV layout must fit the cube faces (2d+2w by d+h pixels).
5. **Export via GeckoLib**: Use `export_geckolib` to produce geo.json, animation.json, and place the texture at `assets/<mod>/textures/entity/<name>.png`.
6. **Generate item model JSON**: Follow our pack's item model conventions (`credit`, `texture_size [128,128]`, `textures` map, `elements` with cube definitions).

**Why this workflow:**
- ✅ Cost-effective: ~$0.60 per generation at Pro tier; free tier gives 3 attempts/month
- ✅ Direct 3D + texture output compatible with GeckoLib/Blockbench pipeline
- ✅ Post-processing step ensures art style matches our pack's established palette
- ✅ Art-agent can refine textures after AI generation
- ✅ Scalable: credits reset monthly; multiple generations per task affordable

**Secondary: Nano Banana Pro + art-agent touch-up**
- Use for generating reference textures or specific decorative elements where procedural 3D isn't needed
- Post-process through art-agent `paint_texture` for pixel-perfect Minecraft style
- Higher cost per image but better for certain texture types (e.g., ornate patterns, text on items)

**Not recommended: Fable 5.2**
- Inappropriate workflow for 2D item textures; designed for 3D procedural scene generation
- Would require building custom bridge code to convert its output to Minecraft format

### Assessment of Art Style Fit

Our pack's item/texture art style is characterized by:
- **Colour palette**: Established browns (~#442c1e for archer elements), greys, and muted tones
- **Convention**: 16x16 or 32x32 pixel art; hard pixels; 3-5 shades per material; top-left lighting; one-pixel darker edges where faces meet
- **Existing reference**: Archer cloak/hood texture `archer_armor.png` averages `#442c1e` brown

**Meshy + post-processing fit**: ✅ Good. The post-processing step (colour palette reduction + Minecraft conventions) can be measured against our existing textures. Average colour of generated texture compared to reference (#442c1e for brown elements) would confirm fit. The art-agent `paint_texture` tool can then touch up any discrepancies.

**Nano Banana Pro fit**: ⚠️ Moderate. Would need more extensive post-processing to restyle realistic outputs into our pixel art palette. Better for generating non-pixel-art elements or reference images.

**Overall assessment**: ✅ **Recommended** — Meshy API pipeline with post-processing is feasible, cost-effective, and produces output compatible with our GeckoLib/Blockbench pipeline. The two-step approach (AI generation + art-agent refinement) gives us both speed and artistic control.

## Plan
1. Create a test generation via Meshy API to validate the workflow
2. Post-process the generated texture to match our pack's art style
3. Build a Blockbench .bbmodel with proper UV mapping
4. Export via GeckoLib and verify the full pipeline works
5. Document the complete reusable workflow for the team

## Notes
- 2026-11-21 15:08 No sign of work for 30 min: marked stalled (whoever ran it stopped).
