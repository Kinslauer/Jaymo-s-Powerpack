---
status: in-review
task: Roast whole animals on spits
branch: claude/project-thread-kxd0tr
with: cloud Claude, Reason Art thread "Roast whole animals on spits"
pr: 1881
mods: Kitchen, Kingdoms
versions: 
started: 2026-11-19
owns:
  - kitchen/src/main/java/com/jaymoskitchen/cooking/spit/
  - kitchen/src/client/java/com/jaymoskitchen/client/spit/
  - kitchen/tools/gen_great_spit.py
  - kingdoms/src/main/java/com/jaymoskingdoms/Firesides.java (cook steps)
  - kingdoms/src/main/java/com/jaymoskingdoms/KitchenSpits.java
---
# Great spit and 3D spit roasts

**Task:** Andy asked whether animals can be cooked on spits, then said yes to a big-game spit for whole gutted carcasses that barbarians use too, with a tearing animation for them and players. From his clip: the cook should stand to the side, meat should be 3D (mutton a shank, steaks), up to 5 on a spit.

## Plan
- Kitchen: great spit block (whole dressed carcass, 3 min roast, torn into its own meat, bones last); spit holds 5 pieces; shank/steak 3D looks; TearPose for anyone tearing; spit has no collision.
- Kingdoms: Firesides cook stands 2 blocks off the fire, loads a piece per sitter (max 5), dresses a beast's body lying near the fire, carries it and roasts it whole.

## Notes
- Follow-up promised: camp hunters, farmer kills leaving carcasses, butchers dressing (see food-getting audit in the thread).

## Done
PR #1881. Kitchen + Kingdoms compile clean; not played. Lore paragraph "The great spit" (new) awaits Andy.
