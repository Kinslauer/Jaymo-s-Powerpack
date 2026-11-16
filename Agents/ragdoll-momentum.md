---
status: in-review
task: ragdoll-momentum - killed and heavy-hit mobs pop into the air instead of falling with the blow
branch: claude/ragdoll-momentum
with:
pr: 1640
mods: kitchen
versions:
started: 2026-11-16
owns:
  - kitchen/src/main/java/com/jaymoskitchen/ragdoll/RagdollBody.java
  - kitchen/src/main/java/com/jaymoskitchen/ragdoll/MobRagdolls.java
  - kitchen/src/main/java/com/jaymoskitchen/corpse/Corpses.java
  - kitchen/src/main/java/com/jaymoskitchen/corpse/CorpseRagdoll.java
  - tools/ragdoll_sim/RagdollSimCheck.java
  - changes/kitchen/claude-ragdoll-momentum.md
---

# Ragdoll momentum

**Task:** Andy: "When mobs are killed or hit with a strong blow they should instantly be affected by the ragdoll, so the momentum carries them in the proper direction right now they are dying then popping into the air unrealistically."

## Cause

`RagdollBody.standing()` started every foot sunk one radius into the ground. The push out on the first step was kept as upward speed, so the body hopped 0.1-0.55 blocks and lost the blow's push in the air (sim against master: player up to 0.31, cow up to 0.55).

## Done (PR #1640, worktree E:/jaymospowers-build/ragdoll-momentum)

- Standing bodies start on the ground.
- Killed by a blow (the killing damage has a source position): no keel-over, limp at once along the blow. Andy approved: deaths from nowhere still keel over.
- The keel-over was always cut short after about 3 ticks by the air check; it now counts a part within its radius of a block top as on the ground.
- A limp mob killed after its own tick keeps its body for its corpse.
- ragdoll_sim: new "killed by a blow" section; all checks pass. Not built locally (CI builds it); not tested in game.

Open for Andy: bodies now slide instead of flying, so they stop sooner (player 2.5 blocks vs 3.5 on a 0.42 knock). `DEATH_CARRY` 0.6 in Corpses is the dial if he wants more carry.
