---
name: lootr-mimics
description: Lootr chests and our mimics; Loot Integrations declined (2026-10-16)
metadata:
  type: project
  modified: 2026-10-16T08:47:02.436Z
---
2026-10-16 Andy asked whether Lootr chests drop correctly. Lootr's chest extends ChestBlockEntity and keeps the vanilla loot table, but its unpackLootTable is a no-op, so 6% mimics woke empty and the chest vanished for all. Fixed: Monsters.mimic rolls the table itself. PR #287 MERGED (pack 0.11.106 / Kingdoms 0.7.78). Next free: 0.11.107 / 0.7.79.
Our own buildings (camps, castles, wrecks) fill chests directly, so they are shared chests, not Lootr ones.
Loot Integrations (CF 580689, ARR, needs Cupboard, Fabric jar built for 26.1): Andy said "we dont need it". Don't add.
**How to apply:** any code checking ChestBlockEntity/getLootTable must allow for Lootr chests.
