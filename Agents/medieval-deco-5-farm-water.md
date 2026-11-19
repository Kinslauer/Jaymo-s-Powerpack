---
status: working
task: medieval-deco-5-farm-water
branch: claude/medieval-deco-5
with:
pr:
mods: kitchen, powers
versions:
started: 2026-11-18
owns:
  - kitchen/src/main/java/com/jaymoskitchen/farmwater/
  - kitchen/src/main/java/com/jaymoskitchen/compat/TideCompat.java
  - kitchen/src/main/java/com/jaymoskitchen/mixin/EatBlockGoalMixin.java
  - kitchen/src/main/java/com/jaymoskitchen/mixin/RaidGardenGoalMixin.java
  - kitchen/tools/gen_farm_water.py
  - src/main/java/com/jaymospowers/fishing/Flotsam.java
---
Medieval deco patch 5 (water barrel, fish barrel, trough, watering can, flotsam, fish trap, scarecrow). Compiles by CI only.
