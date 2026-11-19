---
status: running
task: "[[plan-ghosts-litematica-tools]]"
branch: claude/plan-ghosts-litematica-tools
with: 
pr: 
mods: kingdoms
versions: 
started: 2026-11-19
owns:
  - kingdoms/src/client/java/com/jaymoskingdoms/client/PlanGhosts.java
  - kingdoms/src/client/java/com/jaymoskingdoms/client/MasonScreen.java
  - kingdoms/src/main/java/com/jaymoskingdoms/PlanSites.java
  - kingdoms/src/main/java/com/jaymoskingdoms/MasonTable.java
  - kingdoms/src/main/java/com/jaymoskingdoms/MasonNet.java
  - kingdoms/src/main/resources/assets/jaymoskingdoms/lang (building-plan lines only)
  - changes/kingdoms/plan-ghosts-litematica-tools.md
  - kingdoms/src/client/java/com/jaymoskingdoms/client/PlanMaterialsScreen.java (new)
  - kingdoms/src/client/java/com/jaymoskingdoms/client/mixin/PlanLayerScrollMixin.java (new)
  - kingdoms/src/client/resources/jaymoskingdoms.combat.client.mixins.json (one line)
  - kingdoms/src/main/resources/assets/jaymoskingdoms/lang/parts/plan-tools.json (new)
---
# Building plans: wrong-block colours, layer view, full material list

**Task:** Litematica-style tools for our building plans: colour wrong/missing/extra blocks on a laid plan, show one
layer at a time while holding the plan, and a full material list against your inventory.

## Plan
- Ghost colours (PlanSites sends air spots, PlanGhosts colours wrong/extra)
- Layer view (client keybind + scroll while holding the plan, action bar)
- Full material list (server computes with build substitutes, MasonScreen scrolling list)

## Notes
- Builds on the Deck or CI, not the PC (Andy's PC runs only the game).

## Done
