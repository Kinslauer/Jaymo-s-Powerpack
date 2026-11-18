---
status: open
for: hermes-a
from: pc-claude
asked: 2026-11-18
claimed_by: 
pr: 
---

# Harness 2: new body pieces and two new places

**Andy's words (2026-11-18):** "done ticking put the list on the taskboard for a bot, maybe apollo". Start after harness-helms-finishes-crests merges.

**Rows:**
- **bd-gothic** Fluted cuirass (S) and **bd-globose** Rounded breastplate (S): two more makes of cuirass, chosen at the smith's anvil the way a finish is.
- **bd-coat** Coat of plates (M): plates riveted inside a cloth coat in the realm's colour; chestplate slot, takes the realm colour like the doublet; cheaper than plate, a little weaker.
- **bd-gorget** Gorget (M): plate collar. Needs a new NECK place in `Harness.Slot` (keep saved harnesses loading: append, don't reorder).
- **bd-tassets** Faulds and tassets (M): hooped plate skirt and hip plates. Needs a new WAIST place.
- **bd-rondels** Rondels (S): armpit discs shown on plate spaulders; small armour bonus.
- **bd-tabard** Heraldic tabard (S): realm's arms front and back; the OVER place beside giornea and cloak; a realm's tailor sells it.

**Where to start:** Kingdoms `HeraldryArmour` (pieces, finishes, helms, crests), `HeraldryWear` (who wears and sells what), `Harness` (the ten places), `HarnessMenu`/`HarnessScreen`, client `HeraldryLayer`, `kingdoms/tools/gen_heraldry_armour.py`.

**Source:** Andy ticked every row of the Harness Pattern Book (https://claude.ai/artifact/GSAKVequm61NJT5Z3mLNoW, db picks/andy, saved 2026-11-18 07:30 UTC), no notes. Lore: "The armourers' wider makes" in Lore.md, after "The knight's harness" (marked (new), waiting on Andy).

**Rules:** CLAUDE.md as always: one PR, change note in `changes/kingdoms/`, no version bumps, art drawn by `kingdoms/tools/gen_heraldry_armour.py` in the harness's own style, tuning constants at the top of each class, check 26.3 APIs against genSources, builds on the Deck or CI only (never Andy's PC). The five harness tasks all touch `HeraldryArmour` and the generator: take them in order (helms, body, creeds/lands, barding, upkeep) and start each from master after the one before it merges.

## Notes
-
