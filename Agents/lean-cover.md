---
status: in-review
task: lean-cover
branch: claude/lean-cover
with: ragdolls track (separate agent)
pr: 
mods: kingdoms, powers
versions: 
started: 2026-11-16
owns:
  - kingdoms/src/main/java/com/jaymoskingdoms/Lean.java
  - kingdoms/src/client/java/com/jaymoskingdoms/client/LeanClient.java
  - kingdoms/src/client/java/com/jaymoskingdoms/client/mixin/fifth/CameraLeanMixin.java
  - kingdoms/src/client/java/com/jaymoskingdoms/client/mixin/fifth/AvatarLeanMixin.java
  - kingdoms/src/main/java/com/jaymoskingdoms/Cover.java
  - kingdoms/src/main/java/com/jaymoskingdoms/RangedTactics.java
  - src/main/java/com/jaymospowers/power/chrono/future/FutureCover.java
  - changes/kingdoms/lean-cover.md
  - changes/powers/lean-cover.md
---
# Lean and cover (Ragdolls and Cover picks ln-player, ln-cover, ln-npc)

**Task:** Q/E lean while sneaking, cover that counts while leaning, marksmen/gunmen/archers/future gunners take cover and duck to reload.

## Plan
- Lean lives in Kingdoms (next to Cover and the guns): synced attachment Lean.DIR, C2S packet, camera + model mixins.
- RangedTactics gets under-fire and reload ducking, in-place cover and a cover cache; Powers' FutureGuns get FutureCover.
- Built by CI only (Andy's PC runs only the game).

## Notes
- Camera roll sign and model tilt sign are reasoned, not seen: check in game.
