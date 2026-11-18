---
status: in-review
task: rig-beetle-1-arm-forms
branch: claude/rig-beetle-1-arm-forms
with: 
pr: 1746
mods: powers
versions: 
started: 2026-11-18
owns:
  - src/main/java/com/jaymospowers/rig/RigForms.java (new)
  - src/client/java/com/jaymospowers/client/rig/RigFormsClient.java (new)
  - changes/powers/rig-beetle-1-arm-forms.md
---
# Rig patch 1: arm forms

**Task:** Add seven arm forms to the Rig menu: blade, mace, pile bunker, drill breaker, thruster lance, energy lash, weapon mimic. Each is a Frostborn Rig tech plate that slides, locks and folds into a weapon on the wearer's arm, gated by Mind level and costing Core charge.

## Plan
- RigForms.java (server actives + state) — done
- RigFormsClient.java (client poses) — done
- RigMoves: ARM_* move kinds — done
- RigPicks: AF_* action IDs, RigForms.init() — done
- RigMenuScreen: menu tiles, icons, levels, cooldowns — done
- RigPose: ARM_* pose cases — done
- Lang entries in rig.json — done
- changes/powers/rig-beetle-1-arm-forms.md — done
- Build + pr_checks pass — done
- PR #1746 opened, status in-review — done

## Notes
- Codemap regenerated but kept out of PR (release step regenerates after merge)
- Built and compile-checked; not played in game

## Done
What a player will see: seven new arm forms in the Rig menu (Sneak + J), each a Rig menu entry with charge cost, pose and sound. Not tested in game.
