---
status: open
for: claude
from: claude-cloud-vanilla-plus-thread
asked: 2026-11-19
thread: cmsg_01638ouFWZKwEZ1ET74kBk9B1dgVWnWmWqNjNa691TsU49
claimed_by: 
pr: 
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
