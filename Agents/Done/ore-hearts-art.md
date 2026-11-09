---
status: done
task: ore-hearts-art
branch: claude/ore-hearts-art
with:
pr: 1167
mods: powers
versions:
started: 2026-11-08
owns:
  - art/heart-crystal/
  - src/main/resources/assets/jaymospowers/textures/block/heart_crystal_ore.png
  - src/main/resources/assets/jaymospowers/textures/block/heart_crystal_ore_deepslate.png
  - src/main/resources/assets/jaymospowers/textures/item/heart_crystal.png
  - src/main/resources/assets/jaymospowers/models/block/heart_crystal_ore.json
  - src/main/resources/assets/jaymospowers/models/block/heart_crystal_ore_deepslate.json
  - src/main/resources/assets/jaymospowers/models/item/heart_crystal.json
  - src/main/resources/assets/jaymospowers/blockstates/heart_crystal_ore.json
  - src/main/resources/assets/jaymospowers/blockstates/heart_crystal_ore_deepslate.json
  - changes/powers/ore-hearts-art.md
---
# Heart crystal ore art

**Task:** Make original heart-crystal ore textures in matching stone and deepslate layouts, plus a readable heart-crystal item sprite and the model/blockstate JSONs. Art only; no registration, worldgen, loot, lang or gameplay Java.

## Plan
- Sample the pack's deep rusty reds and draw original 16x16 textures.
- Keep editable Blockbench projects under `art/heart-crystal/` and render front, side and back views.
- Validate every JSON, build Powers, run PR checks, then open the PR and hand the task to review.

## Notes
- The task's nested change-note path conflicts with current `tools/pr_checks.py`; use the enforced `changes/powers/ore-hearts-art.md` path instead.
- Original artwork; no Scaling Health assets used.

## Done
PR #1167 adds original matching stone/deepslate heart-crystal ore art, a cut-crystal heart item sprite, model/blockstate JSONs and editable Blockbench projects. Full Powers build and PR checks passed; front/side/back renders inspected. Not tested in game; registration, worldgen, drops and health mechanics stay with ore-hearts.
- 2026-11-09 01:34 PR #1167 is merged into master; moved to Agents/Done.
