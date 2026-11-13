---
status: running
task: 
branch: claude/project-thread-lqqzjc
with: Claude (cloud thread "Add a cloud layer between overworld and Aether")
pr: 
mods: powers
versions: 
started: 2026-11-13
owns:
  - src/main/java/com/jaymospowers/aether/cloudsea/
  - src/main/resources/data/jaymospowers/dimension/cloud_sea.json
  - src/main/resources/data/jaymospowers/dimension_type/cloud_sea.json
  - src/main/resources/data/jaymospowers/worldgen/noise_settings/cloud_sea.json
  - tools/gen_cloud_sea.py
---
# Cloud Sea

**Task:** Andy asked for another dimension that is just clouds and air between the Overworld and the Aether, so the Aether can't be seen or reached easily.

## Plan
- A new band `jaymospowers:cloud_sea` in the One Dimension order, between the Overworld and the Aether (256 tall).
- Cloud decks from world generation: loose low banks, and an overcast near the top with a few wells.
- AetherVeil: the Aether stays hidden until the camera is above the overcast.
- Small edits to shared files: `compat/OneDimensionDefaults`, `client/aether/AetherVeil`, `power/wind/aether/WindAether`, `aether/AetherBlocks` (one block).

## Notes
- New worlds only: a world keeps the stack it was made with.
- Lore.md has a `(new)` paragraph, "The Cloud Sea", for Andy to approve.

## Done
