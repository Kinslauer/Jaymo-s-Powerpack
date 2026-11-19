---
status: open
for: claude
from: claude-cloud-village-mods-thread
asked: 2026-11-19
thread: cmsg_01638ouFWZKwEZ1ET74kBk9BXraqSPmYUB5h3b9yUHxHtb
claimed_by: 
pr: 
---
# Village mods patch 5: Town life and trade hats (Kingdoms, Powers for the hats)

**Andy (2026-11-19):** cross-referenced 12 village mods, picked 61 of 67 rows on https://claude.ai/artifact/Q6keqE2GsoYjxN6Sh64dwa (picks by screenshot), then said "send it to reviewer claude to work on".

**Full plan:** `docs/VillageMods-Plan.md` on branch `claude/project-thread-i13sx7` (read its header rules first). Row ids in brackets are pick-list rows. Not built yet. One PR per patch, branch `claude/village-mods-5`, change note `changes/<mod>/<branch>.md`, never `version=`/MODLOG/codemap. Ideas only from the outside mods (our own code, names, art), except Villager Hats (MIT, notice kept) and the two shipped jars in patch 7.

## Picked
- **A town builds you a house** [mil_gift_house]: offered once at top reputation (`Commissions`, `Homesteads`).
- **What the town is doing** [mil_panel]: a live page on the town board from `TownLedger`, `Upkeep`, `TownProjects`.
- **Goods each people is known for** [mil_signature]: per-culture `TownTrades` offers unlocked by trade done; Kitchen
  makes the foods.
- **The wandering pilgrim** [mil_pilgrim]: a `Tale` chain over the old age temples, hints only (lore section "The old
  age's peoples", already canon).
- **Trade hats** [vh_hats]: Baubles HEAD slot hats ported from Villager Hats (MIT, notice kept).
- **Where hats come from** [vh_drops]: zombie villager drops and Tailors trades, no grid recipe.
- **Your job's hat counts** [vh_job]: `Careers` gives a small friendliness bonus for wearing your trade's hat.

## Checks
- Compile check against the 26.3 jars; every Minecraft API checked against 26.3 sources.
- `python3 tools/pr_checks.py` passes.
- Lore: no new paragraph expected; if one is needed, quote it in full to Andy in the thread for approval first.
