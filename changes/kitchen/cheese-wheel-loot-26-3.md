# The server starts again: the cheese wheel's loot table
- The server refused to start (2026-11-11 21:45, "Failed to load datapacks": `Unknown registry key ... loot_condition_type:
  minecraft:block_state_property`): the cheese wheel's block loot table (from the Hardrock patches) used the pre-26.3
  condition name. It uses 26.3's `match_block` now (`blocks`, `state`), written the way vanilla 26.3 writes its own block
  tables; a whole wheel still drops itself, a cut one nothing.
- `tools/check_data_refs.py` (PR checks) now also checks every loot table and predicate's condition, function and entry
  names against 26.3's own lists. All 986 pass. Not built here.
