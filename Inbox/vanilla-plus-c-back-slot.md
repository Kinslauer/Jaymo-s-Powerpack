---
status: in-review
for: claude
from: claude-cloud-vanilla-plus-thread
asked: 2026-11-19
thread: cmsg_01638ouFWZKwEZ1ET74kBk9BDKgLe1FfE4xXWhEAVHNACE
claimed_by: cloud
pr: https://github.com/Kinslauer/Jaymo-s-Powerpack/pull/2102
---
# Vanilla+ patch C: back weapon slot by default, YDM with a tool belt (Powers)

**Andy (2026-11-19):** "make this the default and vdm unlocks when using a tool belt" (Back Weapon Slot row; "vdm" = YDM's Weapon Master). He approved the plan and said "Put this work on the board".

**Full plan:** `docs/VanillaPlus-Plan.md` on branch `claude/project-thread-yw4at0` (PR #1796; on master once it merges). Read its header and the patch section first. One PR per patch, branch `claude/vanilla-plus-c-back-slot`, change note `changes/<mod>/<branch>.md`, never `version=`/MODLOG/codemap. No new original mobs (Andy, 2026-11-19: "i dont want any new original mobs until we can model and texture them better").

## Picked
- New Baubles **back** slot for one weapon or tool, drawn on the back; an unbound key swaps it with the held item.
- `WeaponMasterShownMixin`: without a worn Traveler Tool Belt, YDM draws only the back slot item (drop `MAX_SHOWN` 3); with one, everything as now.

## Checks
- Compile check against the 26.3 jars; every Minecraft API checked against 26.3 sources.
- `python3 tools/pr_checks.py` passes.
- Lore: none expected; if one is needed, quote it in full to Andy first.

## Notes
- 2026-11-24 17:57 2026-11-24 PR #1796 merged 2026-11-19: docs/VanillaPlus-Plan.md is on master, start from master. Horse Expert itself shipped in #1796.
- 2026-11-24 17:58 2026-11-24 Andy: "use the latest ruling". #2044 (2026-11-23, every weapon in its set place on the body, Edward Kenway look) wins: drop the 'YDM draws only the back-slot item without a tool belt' part and don't touch MAX_SHOWN/WeaponMasterShownMixin for it. Only the Baubles back slot (drawn on the back, fitting #2044's back placement) and its swap key remain.
- 2026-11-24 Built on branch claude/project-thread-uewdnx with all of A to K in one PR (Andy: "work straight through, one PR"): https://github.com/Kinslauer/Jaymo-s-Powerpack/pull/2102
