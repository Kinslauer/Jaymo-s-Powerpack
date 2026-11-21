---
status: running
task: mutant-zombie-reskin
branch: claude/mutant-zombie-reskin
with: 
pr: 
mods: powers
versions: 
started: 2026-11-21
owns:
  -
---

# Mutant zombie reskin

**Task:** Reskin the mutant zombie to match the reference zombie: cyan chestplate, blue pants, walking zombie appearance. Art/modelling task — model, texture and (walk) animation only, no gameplay Java.

## Plan
- Create the mutant zombie model/texture/walk animation with the art agent in E:/wt/mutant-zombie-reskin/tools/art-agent, briefed on cyan chestplate + blue pants zombie.
- Iterate previews with vision_analyze until front/side/back are right (no missing faces, stray cubes, texture gaps).
- Install exported files into jaymospowers, keep the editable .bbmodel next to the model.
- Add a change note under changes/powers/, build, run pr_checks.py, commit, push, open PR.

## Notes
- Reference image was attached on Discord (thread 1569107033607643388) and is not available locally; briefing from the task text instead (cyan chestplate, blue pants, grassy plain, first-person zombie view).
- Mutant Monsters mod supplies the mutant zombie (dependency jar); Powers only tunes its spawns/brewing — confirm how the reskin is applied (texture override vs new mob).

## Done
What a player will see, and what wasn't tested.
