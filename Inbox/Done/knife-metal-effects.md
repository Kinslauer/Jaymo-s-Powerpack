---
status: done
for: hermes
from: hermes (Andy's pick-list, 2026-11-08)
asked: 2026-11-08
thread: 1569144710264791172
claimed_by: hermes-b
pr: 1204
suggested_owns: [src/main/java/com/jaymospowers/tinkering/, kitchen/src/main/java/com/jaymoskitchen/delight/Chopping.java]
writer: hermes-writer
text_pass: 
---

# Knives carry their metal's effect

Tinkering: a knife made of cobalt, slimesteel, manyullyn and the rest does something on hit that matches the metal.

## What's wanted

From the pick-list, row **Knives as real tinkered tools** (Tinker's Delight, All Rights Reserved — idea only): "Cobalt Knife has chance to poison target, Slimesteel Knife has chance to slow target, Manyullyn Knife has chance to weaken target, Queen's Slime Knife has chance to burn target, Hepatizon Knife has chance to wither target."

Andy ticked it. Our knives already cut straw and board items; the per-metal effect is the new part.

## Notes

- Our own numbers and our own effects, tied to `TinkerMaterial`; nothing copied from the source (ARR).
- Same rule as modifiers: the effect belongs on the tool's metal, so a repaired or recast blade keeps it.
- Keep the chances low enough that a knife is a knife, not a wand.
- 2026-11-08 22:56 hermes-b's last run stopped before finishing; handed back as open so it can carry on.
- 2026-11-09 20:11 hermes-b's last text pass stopped before finishing; handed back so it can carry on.
- 2026-11-09 20:38 Text pass: reviewed lang/parts/tinkering.json (4 new trait entries: poisonous, weakening, burning, withering - all consistent 'A [metal] knife sometimes [effect] what it hits.' pattern), changes/powers/knife-metal-effects.md (clear, complete). No rewrites needed. Slimesteel slowing effect lacks trait entry (code gap, not text).
- 2026-11-09 20:45 Review (head 7d3811a): changes needed. 1) The four new metals (TinkerMaterial COBALT, MANYULLYN, QUEEN_SLIME, HEPATIZON; Tinkering.registerKnifeMetals) can't be got in survival: no ore, no recipe, no smeltery melting or alloy (the javadoc says so). Only the slimesteel knife works for a player. Either make them obtainable (ore/alloys in the smeltery, as silver and steel are) or map the five effects onto metals the pack already has, and say which in the PR. 2) Nothing draws the new tools: every tool type has items/tinker_<type>.json and per-part, per-material models (e.g. models/item/tinkering/battlesign/handle_<metal>.json). The PR adds ToolType.KNIFE with no tinker_knife item model or part models, and no part models for the four new metals on any existing tool type, so those tools show the missing texture. Generate them the way the existing tool models are made. 3) Check that the tool station's knife (SMALL_BLADE + TOOL_HANDLE) has its lang name and tooltip in lang/parts/tinkering.json and that the four trait names (poisonous, weakening, burning, withering) are there. The effect code itself is fine: a 10% mark on a knife hit, from the head metal, server side.
- 2026-11-09 20:55 PR #1204 is merged into master; moved to Inbox/Done.
