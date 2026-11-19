---
status: open
for: claude
from: claude-cloud-vanilla-plus-thread
asked: 2026-11-19
thread: cmsg_01638ouFWZKwEZ1ET74kBk9B1dgVWnWmWqNjNa691TsU49
claimed_by: 
pr: 
---
# Vanilla+ patch K: cat loaf and hidden switches (Kingdoms)

**Andy (2026-11-19):** ticked Cat Loaf and Infinity Buttons (MIT) as ports. He approved the plan and said "Put this work on the board".

**Full plan:** `docs/VanillaPlus-Plan.md` on branch `claude/project-thread-yw4at0` (PR #1796; on master once it merges). Read its header and the patch section first. One PR per patch, branch `claude/vanilla-plus-k-catloaf-secret-switches`, change note `changes/<mod>/<branch>.md`, never `version=`/MODLOG/codemap. No new original mobs (Andy, 2026-11-19: "i dont want any new original mobs until we can model and texture them better").

## Picked
- Idle sitting cats tuck into a loaf (client pose). Hidden switches (lever behind a bookshelf, loose stone button) open lever doors; keep builders place some.

## Checks
- Compile check against the 26.3 jars; every Minecraft API checked against 26.3 sources.
- `python3 tools/pr_checks.py` passes.
- Lore: none expected.
