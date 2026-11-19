---
status: open
for: claude
from: claude-cloud-village-mods-thread
asked: 2026-11-19
thread: cmsg_01638ouFWZKwEZ1ET74kBk9BXraqSPmYUB5h3b9yUHxHtb
claimed_by: 
pr: 
---
# Village mods patch 8: Swamp goblins (Kingdoms, Powers for blocks)

**Andy (2026-11-19):** cross-referenced 12 village mods, picked 61 of 67 rows on https://claude.ai/artifact/Q6keqE2GsoYjxN6Sh64dwa (picks by screenshot), then said "send it to reviewer claude to work on".

**Full plan:** `docs/VillageMods-Plan.md` on branch `claude/project-thread-i13sx7` (read its header rules first). Row ids in brackets are pick-list rows. Not built yet. One PR per patch, branch `claude/village-mods-8`, change note `changes/<mod>/<branch>.md`, never `version=`/MODLOG/codemap. Ideas only from the outside mods (our own code, names, art), except Villager Hats (MIT, notice kept) and the two shipped jars in patch 7.

## Picked
- **Stilt villages** [rb_swamp]: a swamp layout in `GoblinVillages`/`GoblinBuildings` on stilts with plank walks.
- **Swamp blocks** [rb_blocks]: toadstool caps and stems, swamp lantern, mossy oak planks, big lily pad, umbrella leaf;
  our own art, `#variable` textures.
- **Goblin bands** [rb_band]: evening fire music in `GreenskinLife`, our own sounds; the gourd shaker item.
- **Gardeners** [rb_gardener], **hedge witch** [rb_hedgewitch], **swamp trades** [rb_trades], **homes at night**
  [rb_home].

## Checks
- Compile check against the 26.3 jars; every Minecraft API checked against 26.3 sources.
- `python3 tools/pr_checks.py` passes.
- Lore: no new paragraph expected; if one is needed, quote it in full to Andy in the thread for approval first.
