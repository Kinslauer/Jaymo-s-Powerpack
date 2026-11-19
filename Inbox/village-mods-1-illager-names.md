---
status: open
for: claude
from: claude-cloud-village-mods-thread
asked: 2026-11-19
thread: cmsg_01638ouFWZKwEZ1ET74kBk9BXraqSPmYUB5h3b9yUHxHtb
claimed_by: 
pr: 
---
# Village mods patch 1: Illager names (Kingdoms)

**Andy (2026-11-19):** cross-referenced 12 village mods, picked 61 of 67 rows on https://claude.ai/artifact/Q6keqE2GsoYjxN6Sh64dwa (picks by screenshot), then said "send it to reviewer claude to work on".

**Full plan:** `docs/VillageMods-Plan.md` on branch `claude/project-thread-i13sx7` (read its header rules first). Row ids in brackets are pick-list rows. Not built yet. One PR per patch, branch `claude/village-mods-1`, change note `changes/<mod>/<branch>.md`, never `version=`/MODLOG/codemap. Ideas only from the outside mods (our own code, names, art), except Villager Hats (MIT, notice kept) and the two shipped jars in patch 7.

## Picked
- **Camp names in their tongue** [vn_tongue_names]: a name pool in `IllagerTongue` from the same syllables as illager
  personal names; `IllagerCamps` uses it instead of `Camps.name`. Existing camps keep their names.
- **Names for every hold** [vn_hold_names]: outposts, mansions and the old holds get a name on first approach, kept in a
  saved map of structure start to name (`IllagerSites`, `SharedPools`).
- **CTOV outposts are holds** [ct_outposts]: its 14 outpost ids join `SharedPools.OUTPOSTS` and the tongue's hold list.
  Needs Patch 7 to matter.
- **Rank by size** [vn_size_words]: lookout, warband camp, host, war seat, in illager words, on the entering message.
- **Scrawl until learned** [vn_unreadable]: tongue points decide whether the name reads clearly.
- **Standard at the gate** [vn_standard]: a tall ominous-banner standard with a carved board; sneak-use to read.
- **Warlord titles** [vn_warlord_title]: "Grekath of Vrakthul" on the boss bar, bounties and villager talk.
- **Muster rolls** [vn_muster]: an extra ledger page with camp name, size, warlord and next target town.
- **Villagers point the way** [vn_hints]: a Rumours/AskTopics line naming the nearest camp and its rough bearing.
- **Waymarks** [vn_waystone]: rare carved waymarks on roads near their holds, naming the hold and distance.
- **Reaped hosts named** [vn_reaped]: `ReapedHost` carries the nearest fallen camp's name.

## Checks
- Compile check against the 26.3 jars; every Minecraft API checked against 26.3 sources.
- `python3 tools/pr_checks.py` passes.
- Lore: no new paragraph expected; if one is needed, quote it in full to Andy in the thread for approval first.
