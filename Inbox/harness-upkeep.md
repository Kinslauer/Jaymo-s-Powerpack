---
status: open
for: hermes-a
from: pc-claude
asked: 2026-11-18
claimed_by: 
pr: 
---

# Harness 5: upkeep (dents, rust and oil, fitting)

**Andy's words (2026-11-18):** "done ticking put the list on the taskboard for a bot, maybe apollo".

**Rows:**
- **wr-dents** Dents and scratches (M): plate shows wear from its durability.
- **wr-rust** Rust and oil (S): plate left wet rusts and weakens; oiling keeps it bright. Stewardship rule: looking after it is rewarded, leaving it alone costs only its shine.
- **wr-fitting** Fitted harness (M): an armourer fits a harness to one player: a little more armour, slows less.

**Where to start:** Kingdoms `HeraldryArmour` (pieces, finishes, helms, crests), `HeraldryWear` (who wears and sells what), `Harness` (the ten places), `HarnessMenu`/`HarnessScreen`, client `HeraldryLayer`, `kingdoms/tools/gen_heraldry_armour.py`. Also `Harness.WEAR_CHANCE` (wear already exists).

**Source:** Andy ticked every row of the Harness Pattern Book (https://claude.ai/artifact/GSAKVequm61NJT5Z3mLNoW, db picks/andy, saved 2026-11-18 07:30 UTC), no notes. Lore: "The armourers' wider makes" in Lore.md, after "The knight's harness" (marked (new), waiting on Andy).

**Rules:** CLAUDE.md as always: one PR, change note in `changes/kingdoms/`, no version bumps, art drawn by `kingdoms/tools/gen_heraldry_armour.py` in the harness's own style, tuning constants at the top of each class, check 26.3 APIs against genSources, builds on the Deck or CI only (never Andy's PC). The five harness tasks all touch `HeraldryArmour` and the generator: take them in order (helms, body, creeds/lands, barding, upkeep) and start each from master after the one before it merges.

## Notes
- 2026-11-18 02:45 Andy approved the lore placement "The armourers' wider makes" (2026-11-18): it's canon now.
- 2026-11-18 19:34 hermes-a's last run stopped before finishing; handed back as open so it can carry on.
