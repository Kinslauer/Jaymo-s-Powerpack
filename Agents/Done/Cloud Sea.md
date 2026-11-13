---
status: done
task: 
branch: claude/project-thread-lqqzjc
with: Claude (cloud thread "Add a cloud layer between overworld and Aether")
pr: 1469
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
New One Dimension worlds have the Cloud Sea between the Overworld and the Aether: low banks, an overcast with wells (a ceiling from below, a floor from above), the Aether hidden until you are above the overcast. Compiled; world generation loaded and generated in a plain 26.3 server; not played in a stacked world.
- 2026-11-13 22:52 PR #1469 is merged into master; moved to Agents/Done.
