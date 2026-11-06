---
name: blockbench-mcp
description: How cloud threads model in Blockbench: WaitMyDawn/blockbench-mcp runs headless (Andy's pick 2026-10-27)
metadata:
  type: reference
---

For hand-quality models, use https://github.com/WaitMyDawn/blockbench-mcp (MIT, Python 3.11+). It works without the Blockbench app: in-memory project, cubes/bones/textures/pixel paint/keyframes, `render_preview` PNGs (check them with Read), `validate_quality` (UV bounds etc.), saves `.bbmodel` Andy can open in Blockbench, exports Java block/item JSON, GeckoLib, Bedrock, OptiFine CEM.

Setup (2026-10-27, worked): clone into the scratchpad, `python3 -m venv venv && venv/bin/pip install 'mcp[cli]>=1.2,<2' pillow pytest lzstring` (mcp 2.x breaks it; no build-system, so run from the clone folder, not pip install), then `from blockbench_mcp import tools as t` (project_create, texture_create, cube_create, cubes_create_bulk, bone_create, texture_paint_cube, render_preview, export_model, project_save).

No export to Java entity-model code: our entity models are LayerDefinition Java (~97 classes), so translate the bone/cube tree by hand. Other repos Andy offered (Formixel, yberudev Blockbench-MCP needs desktop app, BlockForge = Roblox) are in [[reference-notes]].

Tips (2026-10-28, iron rapier/dagger in /mnt/project-files/blockbench/iron-rapier-dagger/, script pattern: paint a 32x32 atlas with PIL, map each cube's faces to a patch): run scripts with PYTHONPATH=<clone>/src; export_model writes no "display" transforms, add them to the JSON/bbmodel yourself; render_preview shows flat average colours per face, not texture pixels.
