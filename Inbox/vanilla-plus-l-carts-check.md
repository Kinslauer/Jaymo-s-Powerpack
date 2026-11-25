---
status: done
for: claude
from: claude-cloud-vanilla-plus-thread
asked: 2026-11-19
thread: cmsg_01638ouFWZKwEZ1ET74kBk9B1dgVWnWmWqNjNa691TsU49
claimed_by: 
pr: 
---
# Vanilla+ patch L: cart gaps from AstikorCarts (Kitchen)

**Andy (2026-11-19):** ticked AstikorCarts Redux (MIT) as a port. He approved the plan and said "Put this work on the board".

**Full plan:** `docs/VanillaPlus-Plan.md` on branch `claude/project-thread-yw4at0` (PR #1796; on master once it merges). Read its header and the patch section first. One PR per patch, branch `claude/vanilla-plus-l-carts-check`, change note `changes/<mod>/<branch>.md`, never `version=`/MODLOG/codemap. No new original mobs (Andy, 2026-11-19: "i dont want any new original mobs until we can model and texture them better").

## Picked
- `CartEntity` already has supply cart, animal cart and plow. Check and build only the gaps: pulling a cart on foot, a 3-wide plow row. If none, close the task with a note.

## Checks
- Compile check against the 26.3 jars; every Minecraft API checked against 26.3 sources.
- `python3 tools/pr_checks.py` passes.
- Lore: none expected.

## Notes
- 2026-11-24 17:58 2026-11-24 PR #1796 merged 2026-11-19: docs/VanillaPlus-Plan.md is on master, start from master. Horse Expert itself shipped in #1796.
- 2026-11-24 19:40 Checked master 2026-11-24 (cloud): both gaps already built. CartEntity lets a player take a cart by the shafts and pull it on foot (CartEntity.java:75, puller may be a Player, handDrag), and the plow tills a 3x3 patch behind it (CartEntity.java:145, :539). Nothing to build; closed.
