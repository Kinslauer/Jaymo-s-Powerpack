---
status: writing
for: hermes-a
from: pc-claude
asked: 2026-11-18
claimed_by: hermes-a
pr: 1744
writer: hermes-writer
text_pass: 
---

# Harness 4: matching barding and caparisons

**Andy's words (2026-11-18):** "done ticking put the list on the taskboard for a bot, maybe apollo".

**Rows:**
- **hb-barding** Matching barding (L): chanfron, crinet, peytral, crupper in the same make and finish as the rider's harness. The tack screen already takes barding (`equestrian/Tackroom`, `TackMenu`); draw on `BreedHorseModel`.
- **hb-caparison** Caparison (M): cloth cover in the realm's colour and arms, over barding.

**Where to start:** Kingdoms `HeraldryArmour` (pieces, finishes, helms, crests), `HeraldryWear` (who wears and sells what), `Harness` (the ten places), `HarnessMenu`/`HarnessScreen`, client `HeraldryLayer`, `kingdoms/tools/gen_heraldry_armour.py`. Also `equestrian/` and client `BreedHorseRenderer`.

**Source:** Andy ticked every row of the Harness Pattern Book (https://claude.ai/artifact/GSAKVequm61NJT5Z3mLNoW, db picks/andy, saved 2026-11-18 07:30 UTC), no notes. Lore: "The armourers' wider makes" in Lore.md, after "The knight's harness" (marked (new), waiting on Andy).

**Rules:** CLAUDE.md as always: one PR, change note in `changes/kingdoms/`, no version bumps, art drawn by `kingdoms/tools/gen_heraldry_armour.py` in the harness's own style, tuning constants at the top of each class, check 26.3 APIs against genSources, builds on the Deck or CI only (never Andy's PC). The five harness tasks all touch `HeraldryArmour` and the generator: take them in order (helms, body, creeds/lands, barding, upkeep) and start each from master after the one before it merges.

## Notes
- 2026-11-18 02:44 Andy approved the lore placement "The armourers' wider makes" (2026-11-18): it's canon now.
- 2026-11-18 12:43 hermes-writer's claim had no sign of work for 30 min: handed back as in-review.
