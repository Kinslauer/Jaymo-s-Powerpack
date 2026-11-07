---
name: loot-tables-26-3-format
description: 26.3 loot tables need modifier/condition/type keys; old functions/conditions only worked via Moog's Structures upgrade (fixed #887)
metadata:
  type: project
---
26.3 loot table codecs read only `condition` (one object or a predicate id string like "minecraft:tool/can_silk_touch"; several → minecraft:all_of) and `modifier` (object or list), each with `type`; `block_state_property` is now `match_block` (`blocks`, `state`). Old `functions`/`conditions`/`function`/`condition:"..."` keys are silently ignored. 131 of our tables were old-style and only worked because Moog's Structures upgrades pre-26.3 data on the fly (log line "Upgrading pre-26.3 data from 'jaymospowers'"). PR #887 (2026-11-04) converted them.
**How to apply:** write new loot tables in the 26.3 form; grep a dev-server log for "Upgrading pre-26.3 data from 'jaymos" to catch regressions.
