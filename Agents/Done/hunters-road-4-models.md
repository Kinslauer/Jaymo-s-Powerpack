---
status: done
task: hunters-road-4-models
branch: t3/hunters-road-4-models-v2
with: the HR 4 art set - river drowned, mire hag + mud scoop block, grave hag, fog-wight + decoy wisp (models, textures, animations)
pr: 2356
mods: kingdoms
versions: 0.14.21
started: 2026-11-27
owns:
  - kingdoms/art/{river_drowned,mire_hag,grave_hag,fog_wight,fog_wight_wisp,mud_scoop}.bbmodel
  - kingdoms/src/main/resources/assets/jaymoskingdoms/geckolib/models/entity/{river_drowned,mire_hag,grave_hag,fog_wight,fog_wight_wisp,mud_scoop}.geo.json
  - kingdoms/src/main/resources/assets/jaymoskingdoms/geckolib/models/block/mud_scoop.geo.json
  - kingdoms/src/main/resources/assets/jaymoskingdoms/geckolib/animations/entity/{river_drowned,mire_hag,grave_hag,fog_wight,fog_wight_wisp,mud_scoop}.animation.json
  - kingdoms/src/main/resources/assets/jaymoskingdoms/geckolib/animations/block/mud_scoop.animation.json
  - kingdoms/src/main/resources/assets/jaymoskingdoms/models/block/mud_scoop.json
  - kingdoms/src/main/resources/assets/jaymoskingdoms/textures/entity/{river_drowned,river_drowned_glow,grave_hag,grave_hag_glow,mire_hag,mire_hag_glow,fog_wight,fog_wight_glow,fog_wight_wisp,fog_wight_wisp_glow,mud_scoop}.png
  - kingdoms/src/main/resources/assets/jaymoskingdoms/textures/block/mud_scoop.png
  - kingdoms/tools/gen_drowned_hags_fog.py
  - kingdoms/tools/drowned_hags_fog/
  - changes/kingdoms/hunters-road-4-models.md
  - docs/previews/hunters-road-4-models/
---

# Hunter's Road 4 models: the drowned, hags and fog

**Task:** Make the Blockbench models, textures and animations for every new creature in `Picklists/HuntersRoad-Plan.md` section "4. The drowned, hags and fog (Kingdoms `monsters/`) rows B2 B3 B4" - our own vanilla-style designs, ready for the patch 4 code (task hunters-road-4).

## Plan
- Model and animate each creature in Blockbench (`kingdoms/art/*.bbmodel`), export GeckoLib geometry and animations under `assets/jaymoskingdoms/geckolib/`, paint entity and block textures under `assets/jaymoskingdoms/textures/`, preview renders in `docs/previews/hunters-road-4-models/`, generator scripts in `kingdoms/tools/drowned_hags_fog/`.

## Notes
- Started by Antigravity on T3 thread mcp:e7b29b66 as commit a606ad843f; it hit its weekly quota before pushing, and Claude reviewed the commit as passing.
- 2026-11-27 10:08 PM: hermes-b took the takeover, re-verified (root + kingdoms builds, pr_checks, art_audit), pushed the branch and opened PR #2356.

## Done
What a player will see: nothing until the Hunter's Road 4 patch lands; then four new Kingdoms monsters (river drowned, mire hag with her mud scoop block, grave hag, fog-wight with decoy wisp) with our own models, textures and move sets. Not tested in game: assets only, nothing references them yet.
- 2026-11-28 03:42 PR #2356 is merged into master; moved to Agents/Done.
