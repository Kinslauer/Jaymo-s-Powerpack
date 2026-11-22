---
status: in-review
task: NPCs climb 1-2 block heights like players (Andy 2026-11-22)
branch: npc-ledge-climb
with: PC Claude (Code tab); sister track npc-limb-rules (editor agent)
pr: 1988 (limbs half 1987)
mods: powers
versions: 
started: 2026-11-22
owns:
  - src/main/java/com/jaymospowers/freerun/moves/NpcClimb.java
  - src/main/java/com/jaymospowers/freerun/moves/mixin/WalkNodeClimbMixin.java
  - src/main/java/com/jaymospowers/freerun/moves/mixin/MoveControlClimbMixin.java
  - src/client/java/com/jaymospowers/client/NpcClimbPose.java
  - src/client/java/com/jaymospowers/client/mixin/NpcClimbRendererMixin.java
---
# NPC ledge climb

**Task:** "NPCs should have all the same capabilities as the player, meaning they should be able to climb up one and two block heights, and limb damage/effects". This note is the climb half; the limb half is branch npc-limb-rules.

## Plan
- Humanoid mobs path up 2-block edges and over fences (WalkNodeEvaluator mixin), pull up instead of jumping (MoveControl mixin), pose with the player's mantle clip.

## Done
Built by CI only, not played.
