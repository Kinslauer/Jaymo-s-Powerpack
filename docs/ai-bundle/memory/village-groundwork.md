---
name: village-groundwork
description: Village terrain levelling (beard_box village overrides + Groundwork groundskeepers), merged PR #96, Kingdoms 0.5.58 / pack 0.8.112
metadata:
  type: project
  modified: 2026-10-13T05:48:28.392Z
---
2026-10-13: Andy asked to level village/kingdom terrain (villagers stuck in gaps). Cause: Epic Structures: Villages only
swaps templates; vanilla village structures use `beard_thin`. Fix in Kingdoms (PR #96, merged 2026-10-13): `data/minecraft/worldgen/
structure/village_*.json` overrides set `beard_box` (new chunks only), and `Groundwork` (wraps builders' brain) makes the
last builder of each crew fill pits, ramp 2-5 high ledges every 6 blocks, and bridge road gaps up to 5 wide.
Andy said to skip screenshots for this one (took too long); playtest only checked fixes happen (129 in ~7 min).
**Why:** cloud playtests are slow; Andy prefers getting it pushed.
**How to apply:** tune numbers at the top of `Groundwork`. `Seats.structureAt(..., h -> true)` stays UNREADY forever
near big structures like mineshafts, so pass `Seats::village`. Gamerule advance_time false stops game time (breaks
`now % N` ticks) in playtests.
2026-10-13 13:5xZ: Andy saw non-village structures still floating (ruin with grass base over a beach). PR #111 (Kingdoms
0.6.3 / pack 0.9.3, merged 2026-10-13 14:1xZ): `Foundations` + `StructureStartMixin` fill <=6-block air gaps under any surface structure's
pieces at worldgen. Dev Kingdoms server needs a `c:ingots/silver` item tag stub (Powers provides it) to start.
