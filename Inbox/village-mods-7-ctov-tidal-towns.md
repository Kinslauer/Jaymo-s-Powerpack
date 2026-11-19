---
status: open
for: claude
from: claude-cloud-village-mods-thread
asked: 2026-11-19
thread: cmsg_01638ouFWZKwEZ1ET74kBk9BXraqSPmYUB5h3b9yUHxHtb
claimed_by: 
pr: 
---
# Village mods patch 7: CTOV back, Tidal Towns in (Kingdoms, pack)

**Andy (2026-11-19):** cross-referenced 12 village mods, picked 61 of 67 rows on https://claude.ai/artifact/Q6keqE2GsoYjxN6Sh64dwa (picks by screenshot), then said "send it to reviewer claude to work on".

**Full plan:** `docs/VillageMods-Plan.md` on branch `claude/project-thread-i13sx7` (read its header rules first). Row ids in brackets are pick-list rows. Not built yet. One PR per patch, branch `claude/village-mods-7`, change note `changes/<mod>/<branch>.md`, never `version=`/MODLOG/codemap. Ideas only from the outside mods (our own code, names, art), except Villager Hats (MIT, notice kept) and the two shipped jars in patch 7.

## Picked
- **CTOV** [ct_ship]: packMods line for 4.2.2a and off `removedIdsFromProfile`; `CtovHalls` and `Havens.ctov` wake again.
  Check its village pieces load in 26.3 before merging (an old build once stopped the server).
- **Tidal Towns** [tt_ship]: packMods line, credit on the pack page.
- **Tideborn live there** [tt_tideborn]: founded as a village faction at its bell; births lean Tideborn (heritage line
  already canon).
- **Part of the sea's life** [tt_sea]: `SeaTrade`/`TradeRoutes` stops, a pirate raid target, fishers' routines.

## Checks
- Compile check against the 26.3 jars; every Minecraft API checked against 26.3 sources.
- `python3 tools/pr_checks.py` passes.
- Lore: no new paragraph expected; if one is needed, quote it in full to Andy in the thread for approval first.
