---
status: open
for: claude
from: claude-cloud-vanilla-plus-thread
asked: 2026-11-19
thread: cmsg_01638ouFWZKwEZ1ET74kBk9B1dgVWnWmWqNjNa691TsU49
claimed_by: 
pr: 
---
# Vanilla+ patch D: Horse Expert in the skill tree (Kingdoms)

**Andy (2026-11-19):** "add to the tree" (Horse Expert row), then "i meant skill tree". He approved the plan and said "Put this work on the board".

**Full plan:** `docs/VanillaPlus-Plan.md` on branch `claude/project-thread-yw4at0` (PR #1796; on master once it merges). Read its header and the patch section first. One PR per patch, branch `claude/vanilla-plus-d-horse-expert-skill`, change note `changes/<mod>/<branch>.md`, never `version=`/MODLOG/codemap. No new original mobs (Andy, 2026-11-19: "i dont want any new original mobs until we can model and texture them better").

## Picked
- Horseman rank 1 (`Skills.HORSEMAN`) reads a mount's stats without Horse Expert's monocle: compat mixin on `fuzs.horseexpert.common.client.handler.AttributeOverlayHandler.getInspectableEntity`. Monocle still works. One line on the Horseman tile.

## Checks
- Compile check against the 26.3 jars; every Minecraft API checked against 26.3 sources.
- `python3 tools/pr_checks.py` passes.
- Lore: none expected.
