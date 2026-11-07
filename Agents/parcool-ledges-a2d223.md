---
status: running
task: Inbox/parcool-ledge-and-wall-moves.md
branch: claude/parcool-ledges-a2d223
with: 
pr: 
mods: Powers
versions: Powers 0.21.52
started: 2026-11-07
owns:
  - src/main/java/com/jaymospowers/core/Ledge.java
  - src/client/java/com/jaymospowers/client/LedgeClient.java
  - src/client/java/com/jaymospowers/client/LedgePose.java
  - src/main/java/com/jaymospowers/freerun/Freerun.java
  - src/client/java/com/jaymospowers/client/freerun/FreerunClient.java
  - src/client/java/com/jaymospowers/client/freerun/BarHangPose.java
  - src/main/java/com/jaymospowers/freerun/WallSlide.java (new)
  - src/main/resources/assets/jaymospowers/lang/parts/freerun-ledge.json (new)
---
# parcool-ledges-a2d223

**Task:** ParCool track A: ledge shimmy, castaway, wall slide, moving along chains and bars (our own code after ParCool's ideas).

## Plan
- Ledge shimmy (A/D while hanging), castaway (Jump+Back), wall slide (empty-hand right-click while falling by a wall), bar/chain traversal in Bar Swing.
- WallSlide gets a public hook for track B's traceur gloves.

## Notes
- 

## Done
