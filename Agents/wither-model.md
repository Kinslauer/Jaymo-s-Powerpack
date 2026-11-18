---
status: blocked
task: wither-model
branch: claude/wither-model
with: 
pr: 
mods: kingdoms
versions: 
started: 2026-11-18
owns:
  - kingdoms/art/wither.bbmodel
  - kingdoms/art/wither.png
  - docs/Wither-Model-Spec.md
---
# Wither: new model, animations and sounds (modelling bot)

**Task:** Create a vanilla Wither drawn as a Blockbench model in the pack's style, with animations for all its fight moves and own sounds. Previews to Andy first before any changes go into the mod.

## Plan
- Write Wither-Model-Spec.md (completed)
- Create wither.bbmodel in kingdoms/art/
- Render model previews using bbrender.py / bbmodel_preview.py
- Get Andy's approval on previews
- Convert to GeckoLib format once approved
- Add change note to changes/kingdoms/
- Build and verify compiles

## Notes
- 2026-11-18: restarted after Andy sent the first attempt back (invalid JSON .bbmodel, no render, spec wrongly gave the Wither four arms).
- Shape approved in draft: big centre skull (12x12x12) and two smaller side skulls (8x8x8) on a 28x6x8 shoulder bar, centre skull higher; 20x14x10 body core beneath; 6x12x2 spine hanging from the body centre with three rib pairs; 4x12x4 tail at the bottom. About 3.5 blocks tall, like vanilla. No arms, no legs.
- Texture: 128x128 single sheet embedded in the .bbmodel; source copy at kingdoms/art/wither.png. Bone skulls with dark eye sockets, violet (0x6A3FA0) accents, dark stony body/bar/spine/ribs/tail.
- Animation: "idle" (4s loop: hover bob, centre skull looks side to side, side skulls follow gently). Remaining fight animations (ravage, orbit, beam tell/fire, charge tell/rush, stun, climb, throw blues, spawn, death) only after Andy approves the look.
- Proof: .bbmodel parses as valid JSON; tools/bbmodel_bake.py bakes it to 7 bones / 58 quads with the idle animation; preview sheets rendered to E:/Jaymo Vault Live/Assets/wither-model/ and vision-checked: no missing faces, no texture gaps at cube connections, no stray cubes.
- Previews: wither-rest.png (5 angles), wither-look-left.png, wither-look-right.png (skulls turned), wither-idle.png (mid bob), wither-charge.png (spine arched).
- Waiting on Andy's yes on the look before any GeckoLib conversion, renderer, sounds, or Java goes into the mod. Nothing has been seen in game.

## Done
Spec document rewritten (docs/Wither-Model-Spec.md). Valid editable model (kingdoms/art/wither.bbmodel) plus texture (kingdoms/art/wither.png) committed to the claude/wither-model branch. Preview renders posted for Andy's approval; vault status blocked awaiting his answer.