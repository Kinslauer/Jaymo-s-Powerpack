---
status: open
for: claude
from: claude-cloud-village-mods-thread
asked: 2026-11-19
thread: cmsg_01638ouFWZKwEZ1ET74kBk9BXraqSPmYUB5h3b9yUHxHtb
claimed_by: 
pr: 
---
# Village mods patch 6: Small structures (Kingdoms)

**Andy (2026-11-19):** cross-referenced 12 village mods, picked 61 of 67 rows on https://claude.ai/artifact/Q6keqE2GsoYjxN6Sh64dwa (picks by screenshot), then said "send it to reviewer claude to work on".

**Full plan:** `docs/VillageMods-Plan.md` on branch `claude/project-thread-i13sx7` (read its header rules first). Row ids in brackets are pick-list rows. Not built yet. One PR per patch, branch `claude/village-mods-6`, change note `changes/<mod>/<branch>.md`, never `version=`/MODLOG/codemap. Ideas only from the outside mods (our own code, names, art), except Villager Hats (MIT, notice kept) and the two shipped jars in patch 7.

## Picked
- **Tree structures** [rc_trees]: hermit's hollow oak, bandit lookout in a great spruce, a hanging tree with a crow.
- **Mazes** [rc_maze]: a hedge maze in castle gardens and a changing stone maze under a necropolis.
- **Wayside inns** [rc_inn]: halfway along King's Roads, with innkeeper, stable, board.
- **Cairn caches** [rc_cairn]: stone piles with buried chests; `Rumours` names the nearest.
- **Cave gardens** [rc_flora]: rare tended caves; a Druid may live in the biggest.
- **Design preview** [rc_preview]: a client-side ghost outline on `MasonTable` plots.

## Checks
- Compile check against the 26.3 jars; every Minecraft API checked against 26.3 sources.
- `python3 tools/pr_checks.py` passes.
- Lore: no new paragraph expected; if one is needed, quote it in full to Andy in the thread for approval first.
