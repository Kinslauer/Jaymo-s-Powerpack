---
status: done
task: Chain axe swings like the flail, lasso gets a physics rope like the whip, both with Blockbench models
branch: claude/project-thread-qm96fm
with: Andy
pr: 1534
mods: powers, kingdoms
versions: 
started: 2026-11-14
owns:
  - src/main/java/com/jaymospowers/mortal/ChainAxeItem.java
  - kingdoms/src/client/java/com/jaymoskingdoms/client/forms/LassoRope.java
  - kingdoms/src/client/java/com/jaymoskingdoms/client/forms/LassoSockets.java
  - tools/gen_chain_weapons.py
---
# Chain axe and lasso models

**Task:** Andy: "the chain axe and lasso should operate similar to the lasso and flail, therefore they need blockbench models". He confirmed on a card: chain axe like the flail, lasso like the whip.

## Notes
- Chain axe is a flail to `Lash.isFlail`; its head is the item model `chain_axe_swung` drawn on the chain's end.
- Lasso rope physics is its own small Verlet line in Kingdoms (Kingdoms does not compile against Powers).
- Spec: docs/Chain-Weapons-Model-Spec.md.

## Done
Compiled both mods; not played.
- 2026-11-14 10:51 PR #1534 is merged into master; moved to Agents/Done.
