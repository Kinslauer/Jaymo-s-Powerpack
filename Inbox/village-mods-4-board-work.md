---
status: open
for: claude
from: claude-cloud-village-mods-thread
asked: 2026-11-19
thread: cmsg_01638ouFWZKwEZ1ET74kBk9BXraqSPmYUB5h3b9yUHxHtb
claimed_by: 
pr: 
---
# Village mods patch 4: Board work (Kingdoms)

**Andy (2026-11-19):** cross-referenced 12 village mods, picked 61 of 67 rows on https://claude.ai/artifact/Q6keqE2GsoYjxN6Sh64dwa (picks by screenshot), then said "send it to reviewer claude to work on".

**Full plan:** `docs/VillageMods-Plan.md` on branch `claude/project-thread-i13sx7` (read its header rules first). Row ids in brackets are pick-list rows. Not built yet. One PR per patch, branch `claude/village-mods-4`, change note `changes/<mod>/<branch>.md`, never `version=`/MODLOG/codemap. Ideas only from the outside mods (our own code, names, art), except Villager Hats (MIT, notice kept) and the two shipped jars in patch 7.

## Picked
- **Fair pay** [bt_worth]: an item worth table in `Economy` from recipe and rarity; `TownNotices` and `BoardWork` match
  pay to the ask.
- **Decrees** [bt_decrees]: a decree item (smiths', hunters', farmers', war), up to three pinned per board, steering what
  gets posted; rulers pick from `Upkeep` shortfalls; players can earn one.
- **Deadlines** [bt_deadline]: some notices carry a time, an early bonus, and a small reputation loss when missed; the
  paper yellows.
- **Mixed asks** [bt_mixed]: more than one objective and reward per quest.
- **Asks that fit the land** [bt_local]: biome lists on top of the per-type lists.
- **Rarer notices look rarer** [bt_paper]: plain, sealed vellum, gilt, by `BoardWork` stars.
- **Progress on the notice** [bt_tracker]: held notice shows the count; a toast on completion.
- **Hand in at the board** [bt_turn_in]: drop goods into the board to collect pay when the poster is away.

## Checks
- Compile check against the 26.3 jars; every Minecraft API checked against 26.3 sources.
- `python3 tools/pr_checks.py` passes.
- Lore: no new paragraph expected; if one is needed, quote it in full to Andy in the thread for approval first.
