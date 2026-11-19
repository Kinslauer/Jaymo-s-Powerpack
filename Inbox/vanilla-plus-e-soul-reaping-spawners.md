---
status: open
for: claude
from: claude-cloud-vanilla-plus-thread
asked: 2026-11-19
thread: cmsg_01638ouFWZKwEZ1ET74kBk9B1dgVWnWmWqNjNa691TsU49
claimed_by: 
pr: 
---
# Vanilla+ patch E: Reap a Soul and spawner upgrades (Kingdoms)

**Andy (2026-11-19):** "there needs to be a spell you can learn to harvest souls for the spawners" (Spawners+ row, MIT). He approved the plan and said "Put this work on the board".

**Full plan:** `docs/VanillaPlus-Plan.md` on branch `claude/project-thread-yw4at0` (PR #1796; on master once it merges). Read its header and the patch section first. One PR per patch, branch `claude/vanilla-plus-e-soul-reaping-spawners`, change note `changes/<mod>/<branch>.md`, never `version=`/MODLOG/codemap. No new original mobs (Andy, 2026-11-19: "i dont want any new original mobs until we can model and texture them better").

## Picked
- Death Sorcerer spell Reap a Soul fills soul jars (bottle + soul sand) from dying mobs; jars add charges to a spawner (range, delay, count, quiet) or change its mob (never bosses, villagers, players or named characters); Silk Touch + a full matching jar picks a spawner up. Barrow spawners count as grave robbing. `Fx` effects only.

## Checks
- Compile check against the 26.3 jars; every Minecraft API checked against 26.3 sources.
- `python3 tools/pr_checks.py` passes.
- Lore: approved ("Reaping", Lore.md 2026-11-19).
