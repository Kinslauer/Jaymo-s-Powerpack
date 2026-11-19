---
status: merged
task: NPC fights patch 1, the fighter brain
branch: claude/npc-fights-1-brawler
with: 
pr: 1825
mods: powers
versions: 
started: 2026-11-19
owns:
  - src/main/java/com/jaymospowers/core/brawl/
  - src/main/java/com/jaymospowers/core/gifted/GiftedAI.java
  - src/main/java/com/jaymospowers/core/gifted/GiftKit.java
  - src/main/java/com/jaymospowers/power/zfighter/ZFighterRogue.java (brain calls only)
---
# NPC fights 1: the fighter brain

**Task:** Andy's NPC fight picks F1, F2, F3, F6, F8, F16 plus travel flight (`Brawl.flyTo`) and flying kits per power. All work on players too.

## Done
PR #1825, CI green (Build Powers, Kingdoms, Kitchen, PR checks). Not played. Gaps: afterimage is a MagicFx silhouette; guard arms drawn on rogues only; Patrolmen fixed rank; no Xenoform NPC exists.
