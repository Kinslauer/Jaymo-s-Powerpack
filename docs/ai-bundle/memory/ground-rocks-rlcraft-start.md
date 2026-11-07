---
name: ground-rocks-rlcraft-start
description: Andy 2026-10-24: loose rocks/flint on the ground + RLCraft-style start (flint shards, plant string, flint hatchet, no crafted planks); PR #493 merged 2026-10-25 (master then pack 0.16.4 / Kingdoms 0.7.199)
metadata:
  type: project
  modified: 2026-10-25T00:16:49.959Z
---
2026-10-24 Andy asked for rocks stacked 1-4 like candles (biome-matched stone, punch or right click to pick up, 4 craft to cobble) and loose flint. Then: "use the intro mechanics from RL Craft [...] i really just want to remove craftable planks, and add a flint hatchet you can craft by this route". Andy pasted the RLCraft wiki but did NOT ask for RLCraft's item-pickup mod (right click/crouch to pick up items) or the saw, so those were left out.
Built in Powers `ground/` (PR #493): knap flint on top of a hard block -> 2 shards; shard+stick = Primitive Knife (name kept on Andy's word); knife on grass -> plant fiber; 3 fiber = plant string; shard+string+stick = Flint Hatchet (Armory hand_axe renamed). All `*_planks` crafting recipes dropped in `tinkering/Uncraftable.planks`; axe on a log's top face gives planks (2/3/4 by axe grade).
**How to apply:** don't re-add plank crafting recipes; loose flint stays as a second flint source besides gravel. A saw (4 planks) is a possible follow-up only if Andy asks. See [[no-punching-trees]].
