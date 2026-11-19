---
status: done
for: claude
from: claude-cloud-village-mods-thread
asked: 2026-11-19
thread: cmsg_01638ouFWZKwEZ1ET74kBk9BXraqSPmYUB5h3b9yUHxHtb
claimed_by: pc-claude (village-mods-2 track)
pr: 1842
---
# Village mods patch 2: Blast mending (Kingdoms)

**Andy (2026-11-19):** cross-referenced 12 village mods, picked 61 of 67 rows on https://claude.ai/artifact/Q6keqE2GsoYjxN6Sh64dwa (picks by screenshot), then said "send it to reviewer claude to work on".

**Full plan:** `docs/VillageMods-Plan.md` on branch `claude/project-thread-i13sx7` (read its header rules first). Row ids in brackets are pick-list rows. Not built yet. One PR per patch, branch `claude/village-mods-2`, change note `changes/<mod>/<branch>.md`, never `version=`/MODLOG/codemap. Ideas only from the outside mods (our own code, names, art), except Villager Hats (MIT, notice kept) and the two shipped jars in patch 7.

## Picked
- **Builders mend blast holes** [gv_blast_mend]: `ServerExplosionMixin` notes blocks broken on settlement land; `Upkeep`
  sends one builder per crew by day to put them back from the storehouse.
- **Only monster damage** [gv_blast_owner]: blasts a player or siege caused stay unless the town paid; `SiegeScars` keeps
  sieges.
- **Craters heal in the wild** [gv_blast_wild]: a `Weathering` step on blast-marked chunks puts dirt, then grass and
  flowers, back over a few days. Stone and ores never.

## Checks
- Compile check against the 26.3 jars; every Minecraft API checked against 26.3 sources.
- `python3 tools/pr_checks.py` passes.
- Lore: no new paragraph expected; if one is needed, quote it in full to Andy in the thread for approval first.

## Notes
- 2026-11-19 10:33 PR #1842 is merged into master; moved to Inbox/Done.
