---
status: in-review
task: Three primitive hide armour sets (one per drawn style), each with a darker make
branch: claude/primitive-hide-sets
with: Claude Opus 5.5 (T3 Code sub-agent)
pr: https://github.com/Kinslauer/Jaymo-s-Powerpack/pull/2127
mods: powers, kingdoms (light_armor tag only)
versions: 
started: 2026-11-24
owns:
  - tools/vanilla_style/hide_sets.py
  - tools/vanilla_style/armour.py
  - tools/vanilla_style/masks/
  - tools/armory_assets.py
  - src/main/java/com/jaymospowers/armory/Armory.java
  - src/client/java/com/jaymospowers/client/sound/SoundKinds.java
  - caveman/trapper/barbarian (+ dark_) items, recipes, equipment, textures in Powers
  - kingdoms/src/main/resources/data/jaymoskingdoms/tags/item/light_armor.json
  - Assets/primitive-hide-sets/
---
# Primitive hide sets

**Task:** Andy (Decisions 2026-11-24, "Three primitive hide sets, each with a darker make"): "sorry i want all 3, and
they can have a darker variation made from darker hides".

## Plan
- b-slot-first keeps the caveman ids (Caveman); a-refined becomes Trapper's; c-beast becomes Barbarian.
- Dark makes: shaped with #jaymospowers:dark_hides (Kitchen bear fur, hardened leather; Naturalist bear fur), or a
  light piece + charcoal/coal (crafting_transmute, keeps wear and enchantments).
- Art in tools/vanilla_style/hide_sets.py (run by armour.py); Excalibur graded with grade.py --one.
- Draft PR until Andy approves the previews.

## Notes
- Same stats for all six (no tougher-dark pattern in the codebase).

## Done
Draft PR #2127 (waits on Andy's look at Assets/primitive-hide-sets/). Six sets: Caveman (b, existing ids), Trapper's (a),
Barbarian (c), and Dark makes (bear fur / hardened leather in the leather's place, or a light piece + charcoal or coal).
Lore.md: "The hide-wearers" (new). All 24 icons pass audit.py; pr_checks passes. Not seen in game; CI compiles.
