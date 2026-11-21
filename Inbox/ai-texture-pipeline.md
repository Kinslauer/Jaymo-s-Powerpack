---
status: in-review
for: prometheus
from: tiv (Discord, Hotspot / parthenon / Dashboard thread, 2026-11-18)
asked: 2026-11-18
thread: 1573779807097983078
claimed_by: hermes-modeller
pr: research
suggested_owns: []
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
