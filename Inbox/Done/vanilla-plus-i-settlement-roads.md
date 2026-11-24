---
status: done
for: claude
from: claude-cloud-vanilla-plus-thread
asked: 2026-11-19
thread: cmsg_01638ouFWZKwEZ1ET74kBk9BDKgLe1FfE4xXWhEAVHNACE
claimed_by: cloud
pr: https://github.com/Kinslauer/Jaymo-s-Powerpack/pull/2102
---
# Vanilla+ patch I: roads between towns (Kingdoms)

**Andy (2026-11-19):** ticked Countered's Settlement Roads (MIT) as a port. He approved the plan and said "Put this work on the board".

**Full plan:** `docs/VanillaPlus-Plan.md` on branch `claude/project-thread-yw4at0` (PR #1796; on master once it merges). Read its header and the patch section first. One PR per patch, branch `claude/vanilla-plus-i-settlement-roads`, change note `changes/<mod>/<branch>.md`, never `version=`/MODLOG/codemap. No new original mobs (Andy, 2026-11-19: "i dont want any new original mobs until we can model and texture them better").

## Picked
- Kingdoms lays dirt/gravel paths between a realm's towns along the road-travel routes, with milestones, slowly over days, never on claimed or player-built ground.

## Checks
- Compile check against the 26.3 jars; every Minecraft API checked against 26.3 sources.
- `python3 tools/pr_checks.py` passes.
- Lore: none expected.

## Notes
- 2026-11-24 17:58 2026-11-24 PR #1796 merged 2026-11-19: docs/VanillaPlus-Plan.md is on master, start from master. Horse Expert itself shipped in #1796.
- 2026-11-24 19:42 2026-11-24 Andy picked: King's Roads already covers the port; build only the extra links: every town in a realm gets a road to its capital, besides its nearest neighbours.
- 2026-11-24 Built on branch claude/project-thread-uewdnx with all of A to K in one PR (Andy: "work straight through, one PR"): https://github.com/Kinslauer/Jaymo-s-Powerpack/pull/2102
- 2026-11-24 16:30 PR #2102 is merged into master; moved to Inbox/Done.
