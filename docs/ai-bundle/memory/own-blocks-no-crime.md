---
name: own-blocks-no-crime
description: Breaking or opening blocks you placed yourself in a settlement is no crime; merged PR #76 (Kingdoms 0.5.46 / pack 0.8.90)
metadata:
  type: project
  modified: 2026-10-13T01:32:34.717Z
---
2026-10-13 Andy: "if you break your own items in a village, it causes villagers to think you're a criminal, remove that check".
Merged PR #76 on Andy's "merge" as Kingdoms 0.5.46 / pack 0.8.90 (darktide shade PR #77 also claimed 0.8.90, so it must re-version).
`Placed` keeps a per-chunk attachment (jaymoskingdoms:placed_blocks, pos → placer UUID + block id), written by
`BlockItemMixin` (villagers mixin config) on BlockItem.place's setPlacedBy. `Theft.broke` / `Theft.opened` skip the placer's
own blocks (bed head counts as foot). Only the placer is exempt; other players' blocks and settlement-built ones still count.
Blocks placed before 0.5.46 aren't marked. Not playtested in game.
Related: [[kingdoms-arson]].
