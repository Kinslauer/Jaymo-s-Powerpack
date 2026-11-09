---
status: in-review
for: prometheus
from: hermes (Andy, 2026-11-08)
asked: 2026-11-08
thread: 1569168864384913589
claimed_by: hermes-modeller
pr: 1167
suggested_owns:
  - src/main/resources/assets/jaymospowers/textures/block/heart_crystal_ore.png
  - src/main/resources/assets/jaymospowers/textures/block/heart_crystal_ore_deepslate.png
  - src/main/resources/assets/jaymospowers/textures/item/heart_crystal.png
  - src/main/resources/assets/jaymospowers/models/block/heart_crystal_ore.json
  - src/main/resources/assets/jaymospowers/models/block/heart_crystal_ore_deepslate.json
  - src/main/resources/assets/jaymospowers/models/item/heart_crystal.json
  - src/main/resources/assets/jaymospowers/blockstates/heart_crystal_ore.json
  - src/main/resources/assets/jaymospowers/blockstates/heart_crystal_ore_deepslate.json
---
# Heart crystal ore: art for the ore hearts (modelling bot)

**Andy's words (2026-11-08):** "i approved adding the ore hearts, send it to the modeller."

**What's wanted:** art and models only, no Java. The ore-hearts task (hermes-b) adds the code that generates the ore
deep underground and turns the crystal into permanent health. It needs the look:

- **Heart crystal ore** — a deep, rare ore that reads as a cluster of small red-crystal hearts in stone. Two
  variants, normal stone and deepslate, in the same layout so the ore is recognisable in both.
- **Heart crystal** — the item the ore drops. One sprite, readable at 16x16, that looks like a cut crystal with a
  heart inside it. A flat sprite is fine; if you build it in 3D keep the editable Blockbench file under
  `art/heart-crystal/`.
- Palette should sit with the pack's own reds (blood is a deep, slightly rusty red — see the pack's existing item
  textures) rather than a bright vanilla-redstone look, so it does not read as a redstone ore.

**Credit:** Scaling Health (MIT) had crystal hearts; you may start from its art if it helps, keeping the credit in the
change note, or make our own. Either is fine — Andy's ask is "hearts found in ore", not a specific look.

**Not in this task:** no block or item registration, no worldgen, no loot tables, no lang keys — all of that is the
ore-hearts task. The ids are fixed so the code can point at them:
`jaymospowers:heart_crystal_ore`, `jaymospowers:heart_crystal_ore_deepslate`, item `jaymospowers:heart_crystal`.

**Check your work visually before the PR:** render the item (and the ore textures at 16x16 upscaled) and look at the
PNG with `vision_analyze`: the ore must read as ore in both variants, the item must read as a heart crystal, no
missing faces, no muddy pixels.

**PR:** the textures plus the model/blockstate JSONs, the change note `changes/powers/claude/ore-hearts-art.md`, and a
"not tested in game" line. No version bump, no MODLOG, no codemap.

## Notes

- The mechanics (survive death or not, the cap, where it shows) are Andy's calls and are still open on the ore-hearts
  task; they change nothing about the art, so this can run now.
