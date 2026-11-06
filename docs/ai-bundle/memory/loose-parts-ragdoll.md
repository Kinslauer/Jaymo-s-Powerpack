---
name: loose-parts-ragdoll
description: Dead mobs' unknown parts (vulture wings, tails, necks, modded legs) hang loose via Kitchen LooseParts; PR #813 (Kitchen 0.5.40)
metadata:
  type: project
  modified: 2026-10-31T22:32:07.616Z
---
2026-10-31 Andy: "vultures and all other dead mobs should ragdoll instead of falling limp" (they lay stiff). Cause: corpse ragdoll (RagdollPose) only bends vanilla-named limbs; Naturalist vulture (body > neck, tail, rightFoot, right_wing > right_wing_tip) and box-shaped bodies moved nothing.
PR #813 MERGED 2026-10-31 23:4xZ (branch claude/project-thread-xnq1t0, Kitchen 0.5.40, pack 0.19.10, not playtested): client/ragdoll/LooseParts finds the trunk by bulk, hangs every other reaching part toward the ground (spring per corpse, ground clamp, RIGID name list), via RagdollPose.loosen from CorpseRenderer.extractPhysical. Humanoids with right_arm skipped. ModelPartAccessor mixin (children, cubes). Jar: /mnt/project-files/loose-ragdolls/.
Follow-up commit: carried/tumbling/horse-slung bodies get LooseParts too (LooseParts.KEY Frame set in CorpseRenderer.physics, applied from RagdollPose.apply when no RagdollPose); motion keyed by corpse.
See [[physics-mod]], [[corpse-ragdoll-emf]].
