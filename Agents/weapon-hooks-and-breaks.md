---
status: running
task: weapon-hooks-and-breaks
branch: claude/weapon-hooks-and-breaks
with: 
pr: 1713
mods: powers
versions: 
started: 2026-11-17
owns:
  - src/main/java/com/jaymospowers/combat/
  - src/main/java/com/jaymospowers/armory/ArmoryHits.java
  - src/main/java/com/jaymospowers/tinkering/ranged/Ranged.java
---
# Weapon hooks and breaks

**Task:** Andy's four picks: hooks pull climbers down, scythes hook round shields for half damage, mace hits slow, thrown axes may break and mend with 3 nuggets at an anvil.

## Plan
- Shields.hookedBlock + ShieldHookMixin; ArmoryHits slow and drag-down; Ranged break and BrokenAxeAnvilMixin.

## Notes
- Also touched: Freerun.dragOff, ThrownWeapon (one call), two mixin json lists.
