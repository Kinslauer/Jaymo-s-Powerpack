---
name: block-carry
description: Powers 0.8.52/0.8.59 (PRs #33, #46 merged 2026-10-12): sneak+right-click carries player-placed blocks; containers set down only
metadata:
  type: project
  modified: 2026-10-12T07:04:35.745Z
---
2026-10-12 Andy: "shift right click single blocks to carry them like corpses", then: only blocks players placed,
include chests/furnaces/crafting tables, "they just should be thrown" (ambiguous; built as throwable too, a
decision card asked "thrown too" vs "set down only" for containers, unanswered at merge).
PR #33 merged as Powers 0.8.52: `core/CarryBlock` + `Carry.BLOCK` hold; placed blocks tracked per chunk
(`CarryBlock.PLACED`, from `mixin/CarryBlockItemMixin` on BlockItem.placeBlock). Not playtested in game.
Andy answered "Set down only" 07:13Z: PR #46 merged 07:32Z as Powers 0.8.59 (`CarryBlock.holdsContents`: sneak+right-click drops a carried container instead of throwing). Test jar /mnt/project-files/block-carry/.
