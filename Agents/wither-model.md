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
  - art/wither.bbmodel
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
- Spec doc created at docs/Wither-Model-Spec.md
- Minimal .bbmodel created at kingdoms/art/wither.bbmodel
- Texture: 128x128 solid red (placeholder; will be replaced with proper Wither texture)
- Model has: root, body, center head, left head, right head groups
- Status: blocked awaiting Andy's preview approval
- Task referenced in vault: E:/Jaymo Vault Live/Inbox/wither-model.md with status: blocked

## Done
Spec document written and vault status set to blocked. Awaiting Andy's review of model previews before proceeding with GeckoLib conversion and in-game testing.