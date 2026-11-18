---
status: open
for: hermes-a
from: pc-claude
asked: 2026-11-18
claimed_by: 
pr: 
---

# Harness 3: creeds and other lands

**Andy's words (2026-11-18):** "done ticking put the list on the taskboard for a bot, maybe apollo". Start after harness-body-pieces merges (uses the helms, finishes, coat of plates and tabard from tasks 1 and 2).

**Rows:**
- **cr-faith** Faith-marked plate (M): the realm's faith sign etched on the breast; one per each of the eight faiths.
- **cr-chivalry** Oath sleeves (S): a favour tied on the arm; an oathbreaker loses it (ties to the approved pacts lore).
- **cr-conquest** Spiked black plate (S): Conquest realms always blacken (today one realm in three, `HeraldryWear.BLACKENED_REALMS`) and get spiked spaulders.
- **cr-coin** Bought harness (S): Coin lords in etched plate, their hired men in brigandine and barbutes.
- **cr-crown** King's Guard livery (S): matching etched harness and tabards.
- **ld-snowy** Fur-lined harness (M), **ld-desert** Mail and plate of the sands (M: mail over quilted coat, spiked helm with mail veil), **ld-jungle** Quilted cotton armour (S, brine-soaked like the real Aztec ichcahuipilli), **ld-savanna** Hide and bronze (S): those realms now field knights (`HeraldryWear.KNIGHT_LANDS` is plains, taiga, swamp today). Desert raiders keep their Frontier wrappings.
- **ld-cherry** Laced lamellar (M): the blossom lands already have folded lamellar for their lords' guards (Lore.md "Folded lamellar and the pit harness"). Extend that for the blossom realms' knights; don't make a second lamellar.

**Where to start:** Kingdoms `HeraldryArmour` (pieces, finishes, helms, crests), `HeraldryWear` (who wears and sells what), `Harness` (the ten places), `HarnessMenu`/`HarnessScreen`, client `HeraldryLayer`, `kingdoms/tools/gen_heraldry_armour.py`.

**Source:** Andy ticked every row of the Harness Pattern Book (https://claude.ai/artifact/GSAKVequm61NJT5Z3mLNoW, db picks/andy, saved 2026-11-18 07:30 UTC), no notes. Lore: "The armourers' wider makes" in Lore.md, after "The knight's harness" (marked (new), waiting on Andy).

**Rules:** CLAUDE.md as always: one PR, change note in `changes/kingdoms/`, no version bumps, art drawn by `kingdoms/tools/gen_heraldry_armour.py` in the harness's own style, tuning constants at the top of each class, check 26.3 APIs against genSources, builds on the Deck or CI only (never Andy's PC). The five harness tasks all touch `HeraldryArmour` and the generator: take them in order (helms, body, creeds/lands, barding, upkeep) and start each from master after the one before it merges.

## Notes
- 2026-11-18 02:44 Andy approved the lore placement "The armourers' wider makes" (2026-11-18): it's canon now.
