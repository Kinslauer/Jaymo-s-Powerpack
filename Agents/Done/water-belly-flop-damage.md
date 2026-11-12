---
status: done
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
What a player will see, and what wasn't tested.
- 2026-11-11 02:14 No sign of work for 30 min: marked stalled (whoever ran it stopped).
- 2026-11-12 05:20 PR #1263 is merged into master; moved to Agents/Done.
