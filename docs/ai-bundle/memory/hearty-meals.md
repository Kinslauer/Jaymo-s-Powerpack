---
name: hearty-meals
description: Spice of Life Valheim Edition remade as Kitchen "hearty meals" (stomach slots), PR #240, pack 0.11.42 / Kitchen 0.4.43
metadata:
  type: project
---
2026-10-15 Andy asked for Spice of Life: Valheim Edition. It's LGPL-3.0 but only 1.19.2/1.20.1 (no 26.3), so it was remade in Kitchen (`stomach/Stomach`): start 3 hearts, 3 food slots + drink slot, hearts/regen per meal, same food can't double up, refill in last 20%, food health cap 34, +10% speed when full, dishes 1.25x (Perfect 1.5x), Z Fighters +2 slots via KitchenCompat.extraStomachSlots.
**Why:** many powers burn the hunger bar (causeFoodExhaustion / getFoodLevel checks), so the hunger bar was KEPT as sprint/power fuel; only its healing was turned off.
**How to apply:** PR #240 MERGED 2026-10-15 10:1xZ as pack 0.11.45 / Kitchen 0.4.43; not playtested. Tuning constants top of Stomach.
2026-11-05 PR #1015 MERGED (Kitchen 0.5.73): expired meals' still-full hearts stay as `Stomach.LEFTOVER` max health, lost first on any health loss, never healed back (Andy's ask). Not played.
