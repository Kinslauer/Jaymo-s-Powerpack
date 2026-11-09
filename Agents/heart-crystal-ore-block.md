---
status: running
task: heart-crystal-ore-block
branch: claude/heart-crystal-ore-block
with:
pr:
mods: powers
versions:
started: 2026-11-09
owns:
  - art/heart-crystal/
  - src/main/resources/assets/jaymospowers/textures/block/heart_crystal_ore.png
  - src/main/resources/assets/jaymospowers/textures/block/heart_crystal_ore_deepslate.png
  - src/main/resources/assets/jaymospowers/textures/block/heart_crystal_facets.png
  - src/main/resources/assets/jaymospowers/models/block/heart_crystal_ore.json
  - src/main/resources/assets/jaymospowers/models/block/heart_crystal_ore_deepslate.json
  - src/main/resources/assets/jaymospowers/blockstates/heart_crystal_ore.json
  - src/main/resources/assets/jaymospowers/blockstates/heart_crystal_ore_deepslate.json
  - changes/powers/heart-crystal-ore-block.md
---
# Heart crystal ore block

**Task:** Replace the flat red heart ore art with original Blockbench geometry matching Tiv's purple amethyst-heart references: one large central crystal, smaller crystals in a heart-shaped cluster, and matching stone/deepslate hosts.

## Plan
- Keep an opaque full-cube rock host and add raised crystal geometry, avoiding cut-out culling costs.
- Make original bright violet/lilac textures that read lit like vanilla amethyst; this art-only pass does not add light emission.
- Export both block models, retain editable .bbmodel sources, render and inspect front/side/back views, then build and run PR checks.

## Notes
- Replaces the flat three-red-hearts cube_all art merged in PR #1167; the heart-crystal item sprite is unchanged.
- Original geometry and textures, drawn from the supplied references; no outside art or licence is used.

## Done
Work in progress; not tested in game.
