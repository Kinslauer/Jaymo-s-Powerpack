---
status: done
for: claude
from: claude-cloud-vanilla-plus-thread
asked: 2026-11-19
thread: cmsg_01638ouFWZKwEZ1ET74kBk9BDKgLe1FfE4xXWhEAVHNACE
claimed_by: cloud
pr: https://github.com/Kinslauer/Jaymo-s-Powerpack/pull/2102
---
# Vanilla+ patch G: Illager Invasion kinds (Kingdoms)

**Andy (2026-11-19):** ticked Illager Invasion (MIT, reaches 26.2) as a port. He approved the plan and said "Put this work on the board".

**Full plan:** `docs/VanillaPlus-Plan.md` on branch `claude/project-thread-yw4at0` (PR #1796; on master once it merges). Read its header and the patch section first. One PR per patch, branch `claude/vanilla-plus-g-illager-invasion`, change note `changes/<mod>/<branch>.md`, never `version=`/MODLOG/codemap. No new original mobs (Andy, 2026-11-19: "i dont want any new original mobs until we can model and texture them better").

## Picked
- Its illager kinds in Kingdoms camps and raids with temperaments and poses, its MIT models/textures. First check for a 26.3 build: if there is one, ship it and only hook it in.

## Checks
- Compile check against the 26.3 jars; every Minecraft API checked against 26.3 sources.
- `python3 tools/pr_checks.py` passes.
- Lore: place the new illager kinds in Lore.md as (new) and quote it to Andy first.

## Notes
- 2026-11-24 17:57 2026-11-24 PR #1796 merged 2026-11-19: docs/VanillaPlus-Plan.md is on master, start from master. Horse Expert itself shipped in #1796.
- 2026-11-24 Built on branch claude/project-thread-uewdnx with all of A to K in one PR (Andy: "work straight through, one PR"): https://github.com/Kinslauer/Jaymo-s-Powerpack/pull/2102
- 2026-11-24 21:30 PR #2102 is merged into master; moved to Inbox/Done.
