---
status: in-review
task: water-belly-flop-damage
branch: claude/water-belly-flop-damage
with:
pr: 1263
mods: powers
versions: Powers 0.21.109, Kingdoms 0.7.712
started: 2026-11-10
owns:
  - src/main/java/com/jaymospowers/water/Dives.java
---
# Belly flops into water hurt

**Task:** Falling into water from a height should hurt unless the player enters in a dive. Belly flop (flat, face down) or any non-dive landing takes fall damage scaled by height and speed; a proper dive (head first, body roughly vertical) takes little or none; short drops stay harmless. Cannonball (feet first, tucked) is the middle case. Build on water/Dives.java's existing entries (sneak-early = cannonball, sneak-just-before = clean dive, neither = belly flop): raise FLOP_DAMAGE/FLOP_MAX into real fall damage. Clean dive and Leap of Faith's head-first dive stay safe. Add poses everyone can see (cannonball tucked ball, belly flop flat spread + wince, clean dive arms forward) in FreefallPose style; add a loading-screen hint (StoryHoldScreen HINTS); change note in changes/powers/. Remake the idea only (RealisticDamage data pack is 1.20.1 All Rights Reserved; copy nothing).

## Plan
- Read water/Dives.java, freerun/moves/RunMoves, SkyDive, FreefallPose, StoryHoldScreen HINTS; check 26.3 fall-damage/water-entry flow (updateInWaterStateAndDoFluidPushing / checkFallDamage)
- Implement flop damage scaled by fallDistance/speed at water entry, gated on dive pose/entry
- Client poses for the three entries keyed off synced state
- Loading-screen hint + changes/powers/ note
- sh ./gradlew clean compileJava, python3 tools/pr_checks.py, commit, push, gh pr create, vault --set in-review

## Notes
- Numbers (damage per block, caps) are mine to pick; state them in the PR.
- Mobs same rule only if cheap; players first. Wind/flyers and fall-damage-cancelling powers keep protection.

## Done
Belly flops (and cannonballs) into water now deal real fall damage scaled by fall height and impact speed (flop about 6 HP from 12 blocks, capped at 8; cannonball middle case capped at 4); a clean dive looking straight down takes nothing; short drops stay harmless. Entry poses show on everyone in view and ease out under water. Not tested in game: compile-checked (sh ./gradlew clean compileJava passes) and tools/pr_checks.py passes. PR #1263 pushed, awaiting review/merge. Open question flagged in the PR: the loading-screen hint needs Kingdoms' StoryHoldScreen (HINTS is 88 there, 89-92 reserved) plus a lang key - not added since this PR touches Powers only.
