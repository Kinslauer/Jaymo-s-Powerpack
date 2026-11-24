---
status: changes
for: claude-cloud
from: tiv (Discord, Hotspot / parthenon / Dashboard thread, 2026-11-18)
asked: 2026-11-18
thread: cmsg_017LSt6rWz6CzxSGvzDd41XWRihgeu7KNw14feoHe7EEoR
claimed_by: 
pr: research
suggested_owns: []
writer: hermes-writer
text_pass: done
---

# AI texture pipeline for item PNGs and model textures

Andy wants to research an AI-assisted texture generation pipeline similar to the Reddit workflow (Fable 5.2 + Nano Banana Pro + Meshy API + Blender + Unity/URP) but applied to our item PNGs and model textures for Powers/Kingdoms/Kitchen.

## Reference material

Three Reddit posts from r/TopologyAI describing:
- 3D plant/foliage generation using Fable 5.2, Nano Banana Pro (textures), Meshy API (geometry), Blender (cleanup and atlas baking), Unity 6 with URP
- Three LODs (500, 250, 100 triangles), materials reacting to light and wind, shared texture atlases
- Costs: ~$4 Nano Banana Pro, ~$3 Meshy API, ~8 hours agent time on Claude Max subscription for Fable 5.2
- Reusable workflow, full artistic control, lower costs, AAA quality features

## What's wanted

Research how to apply a similar AI-assisted process to our item PNGs and model textures. Determine which tools fit our stack (Minecraft 26.3 Fabric, GeckoLib models, Blockbench .bbmodel), what the cost would be, and what a reusable workflow looks like for generating item textures and model textures in our pack's style.

## Notes

Research/investigation first — no code or art changes yet. The output should be a process recommendation with tools, costs, and steps, plus an assessment of whether it fits our pack's art style and pipeline.

## AI Texture Generation Pipeline Recommendation (writer: hermes-writer)

### Overview
Andy wants to research an AI-assisted texture generation pipeline similar to the Reddit workflow (Fable 5.2 + Nano Banana Pro + Meshy API + Blender + Unity/URP) but applied to our item PNGs and model textures for Powers/Kingdoms/Kitchen (Minecraft 26.3 Fabric, GeckoLib models, Blockbench .bbmodel).

### Tools Assessment

#### 1. Blockbench MCP (GitHub: sosadly/blockbench-mcp)
- AI can create Minecraft Java/Bedrock/GeckoLib models, paint textures pixel by pixel, build rigs, write keyframe animations, install Blockbench plugins
- Texturing engine: detail cubes with hue-shifted light, ambient occlusion, edge bevels, per-material detail (skin, cloth, hair, metal, wood, stone, leather, scales), world-space patterns (plaid, stripes, panels)
- UV packing: pack_uv gives thin cubes per-face UV so every face gets real pixels
- Fits our stack: Direct Blockbench integration, GeckoLib export, works with existing .bbmodel workflow
- Cost: Open-source (self-hosted MCP server); Claude Desktop/Cursor as AI client
- Effort: High setup, but full control over output

#### 2. pixelgen (GitHub: yuuki14202028/pixelgen)
- Stable Diffusion 1.5 fine-tuned for Minecraft item 16x16 textures with transparency
- sketch2tex: doodle → finished item; variants.py: matched tier sets, reinterpreting existing textures
- Fits our stack: Generates actual 16×16 RGBA sprites for resource packs; mc-asset integration for Minecraft validation
- Cost: Free (open-source); requires Stable Diffusion setup
- Effort: Medium; fine-tuning or using pre-trained model

#### 3. mc-asset (GitHub: smile-minecraft/mc-asset)
- Texture and model tool for Minecraft resource packs
- pixelize reference art; quantize colors; generate tiling textures; validate packs; compare assets; palette/material extraction
- Fits our stack: Minecraft-specific; validates against Minecraft requirements (16x16, color counts, alpha); palette inspect for material roles; generate for procedural textures; tile for seam correction
- Cost: Free (open-source)
- Effort: Low-Medium; CLI-driven, MCP-integrable, validated output

#### 4. Minecraft Mod Maker (ZAP-Hosting)
- AI writes Fabric/NeoForge Java code + simple pixel textures, block/item models, language files, recipes; packs into .jar
- Fits our stack: Generates simple textures only; not suitable for our art-style custom items/armor
- Cost: Paid service
- Effort: Low; but art quality too low for our pack

### Recommendation

**Primary tool: mc-asset** — Minecraft-specific, validated output, CLI/MCP integrable, covers the full asset lifecycle. Best fit for our 26.3 Fabric pipeline.

**Secondary tool: pixelgen** — For item-type textures where Stable Diffusion fine-tuning makes sense. Good for rapid prototyping.

**Tertiary tool: Blockbench MCP** — For GeckoLib model-integrated textures and animations where texture must follow 3D geometry. Higher effort but most control.

### Suggested Workflow
1. **Concept phase:** Use `mc-asset generate` with procedural patterns or `pixelgen` with prompts to explore design directions
2. **Refinement phase:** Use `mc-asset pixelize` on reference art or `mc-asset quantize` to reduce color palettes to match our existing style (brown/tan palette: archer cloak averages #442c1e)
3. **Validation phase:** Use `mc-asset validate` to ensure 16x16 RGBA conformance before adding to the pack
4. **Atlas integration:** Manual atlas baking in Blender or Photoshop, following existing texture atlas conventions

### Cost Estimate
- mc-asset: $0 (open-source, self-hosted)
- pixelgen: $0–$50 (SD setup or pre-trained model)
- Blockbench MCP: $0 (open-source) + ~8 hrs Claude time for setup

### Art Style Assessment
Tools produce Minecraft-appropriate 16x16 textures. Main constraint: maintaining our pack's brown/tan palette. All tools support palette control; mc-asset's material remapping and pixelgen's color budget are most compatible with our existing style.

### Next Steps
Prototype a few item textures using mc-asset and pixelgen, compare output quality against existing pack textures, and document the reusable pipeline steps for future pack additions.
- 2026-11-24 18:00 2026-11-24 Andy: "finish here": cloud Claude finishes this in thread cmsg_017LSt6rWz6CzxSGvzDd41XWRihgeu7KNw14feoHe7EEoR; bots leave it.
- 2026-11-24 19:51 Wrap-up doc (Claude): https://claude.ai/artifact/SCVbZJVrMn1aMKn71uUBpP . Tools checked from their pages: mc-asset (MIT, deterministic) = checker/finisher beside tools/art-agent; pixelgen not for shipped art (trained on scraped mod textures incl. ARR); Blockbench MCP only on Andy's PC. Not run here (install denied in cloud, no Gemini key); PC trial steps in the doc.
- 2026-11-24 23:16 claude-cloud's claim had no sign of work for 30 min: handed back as changes.
