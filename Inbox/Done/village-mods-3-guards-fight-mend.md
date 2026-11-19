---
status: done
for: claude
from: claude-cloud-village-mods-thread
asked: 2026-11-19
thread: cmsg_01638ouFWZKwEZ1ET74kBk9BXraqSPmYUB5h3b9yUHxHtb
claimed_by: pc-claude (village-mods-3 track)
pr: 1838
---
# Village mods patch 3: Guards that fight and mend (Kingdoms)

**Andy (2026-11-19):** cross-referenced 12 village mods, picked 61 of 67 rows on https://claude.ai/artifact/Q6keqE2GsoYjxN6Sh64dwa (picks by screenshot), then said "send it to reviewer claude to work on".

**Full plan:** `docs/VillageMods-Plan.md` on branch `claude/project-thread-i13sx7` (read its header rules first). Row ids in brackets are pick-list rows. Not built yet. One PR per patch, branch `claude/village-mods-3`, change note `changes/<mod>/<branch>.md`, never `version=`/MODLOG/codemap. Ideas only from the outside mods (our own code, names, art), except Villager Hats (MIT, notice kept) and the two shipped jars in patch 7.

## Picked
- **Cleric heals** [gv_cleric_heal]: the town healer throws healing splashes on badly hurt guards and villagers, a few a
  day (`FieldMedics`).
- **Smith mends gear** [gv_smith_mend]: armourers and weaponsmiths fix half-worn guard gear and patch golems, paid in
  storehouse iron (`WornGear`, `ShieldShatter`).
- **Guards eat and drink** [gv_eat]: a `Comrades` step; storehouse food; villagers hand spare food to guards.
- **Kick and sidearm** [gv_kick]: rushed archers kick (`Shoves`) and fight with a sidearm (`RangedTactics`).
- **Ring the bell** [gv_bell]: ringing a town bell during an attack calls its guards; false alarms annoy the town.
- **Raiders kill livestock** [gv_raid_beasts]: `Raids` target picks include town animals.
- **Witches go after villagers** [gv_witches]: vanilla witches throw harming potions at villagers; guards answer.
- **Golem pathing** (Andy's note on gv_golem_float, "just give them better pathing and the ability to jump up and gain
  altitude in water"): iron golems (`TownGolem` and vanilla) path around water, step up ledges, and rise in water by
  jumping instead of walking the riverbed.

## Checks
- Compile check against the 26.3 jars; every Minecraft API checked against 26.3 sources.
- `python3 tools/pr_checks.py` passes.
- Lore: no new paragraph expected; if one is needed, quote it in full to Andy in the thread for approval first.
- PR #1838, CI green, not played.

## Notes
- 2026-11-19 10:33 PR #1838 is merged into master; moved to Inbox/Done.
