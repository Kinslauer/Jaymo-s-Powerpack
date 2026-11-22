---
status: done
task: parkour-animations
branch: claude/parkour-animations
with: 
pr: 1959
mods: powers
versions: powers 0.21.85
started: 2026-11-20
owns:
  - src/client/java/com/jaymospowers/client/freerun/ (pose classes: BalancePose, BarHangPose, RunPose, UpRunPose, ClimbFirstPerson)
  - src/client/java/com/jaymospowers/client/freerun/moves/RunMovesPose.java
  - src/client/java/com/jaymospowers/client/LedgePose.java
  - src/client/java/com/jaymospowers/client/power/web/WebParkourPose.java (freerun parts only)
  - src/main/java/com/jaymospowers/freerun/Freerun.java, FreerunWalls.java
  - src/client/java/com/jaymospowers/client/freerun/FreerunClient.java
  - src/main/java/com/jaymospowers/core/Ledge.java, src/client/java/com/jaymospowers/client/LedgeClient.java
  - src/main/resources/assets/jaymospowers/geckolib/animations/entity/player_freerun_*.animation.json (new)
  - src/main/resources/assets/jaymospowers/bbmodels/player_freerun*.bbmodel (new)
---

# Parkour animations: re-key freerun moves, add climb down, corners and adaptive vaults

**Task:** Overhaul Powers freerunning animations using Blockbench/GeckoLib clips (KeyframeClip), re-keying every existing freerun move and adding climb down, around-corner shimmy, and adaptive speed-based vault selection.

## Plan
- Scan freerun classes, plan (docs/CombatAnimations-Plan.md), KeyframeClip usage (CrawlPose, MountClimbPose, SlingPose)
- Part A: re-key existing freerun moves as GeckoLib animation clips
- Part B: climb down, around corners, adaptive speed vault
- Build check + pr_checks.py
- Commit, push, open PR; set task in-review

## Notes
- Reference rule: Unity footage and parkour videos watched for timing/weight/placement only; nothing taken from Unity game files or traced from videos; mocap only from footage we film ourselves
- Lore: no new world thing, no Lore.md paragraph; moves stay in Kingdoms Stealth tree freerunning; climb down and corners go to whoever can climb; adaptive vault to Freerunner 1
- Big patch: split into two tracks that don't share files; 2 at a time

## Done
What a player will see, and what wasn't tested.
- 2026-11-20 23:40 No sign of work for 30 min: marked stalled (whoever ran it stopped).
- 2026-11-22 07:30 PR #1959 is merged into master; moved to Agents/Done.
