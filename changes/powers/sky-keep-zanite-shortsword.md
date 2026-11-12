# The server starts again: the sky village keep's loot
- The server refused to start (2026-11-11 21:33, "Failed to load datapacks": `Unknown registry key ... jaymospowers:zanite_sword`):
  the sky village keep's chest (`loot_table/chests/sky_village/keep.json`) named a Zanite Sword, which doesn't exist; Zanite
  has a shortsword, and the chest gives that now.
- New `tools/check_data_refs.py`, run by the PR checks: a recipe category the recipe book doesn't have, or a recipe,
  loot table or advancement naming an item of ours that doesn't exist, now fails the PR instead of stopping the server.
  Not built here.
