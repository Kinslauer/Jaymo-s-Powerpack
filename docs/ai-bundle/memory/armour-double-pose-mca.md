---
name: armour-double-pose-mca
description: MCA's armour model copies the body's captured pose, so our poses doubled on armour (downed/execution); PR #908 MERGED (Powers 0.20.6 / Kingdoms 0.7.507)
metadata:
  type: project
---
2026-11-04 Andy: "armor seems broken on the new races ... it broke during the execution" (downed Feline's iron armour twisted/scattered).
- Cause: MCA PlayerArmorExtendedModel.setupAnim applies HumanoidModelPose (captured by MCA SkinLayer after our parent poses), no receivesDeferredAnimationPose flag, so PoseBlend laid downed/crawl/finisher poses on it twice.
- Fix PR #908 (branch claude/project-thread-4vyhs0) MERGED 2026-11-04 09:16Z, not played in the full pack: Powers PosedCopy treats MCA armour as a copy; Kingdoms McaLookModels.posedByMca too; BeastParts tails lie toward the feet when the body lies down (BeastParts.lying reads the pose stack).
- Plain standing armour on all new looks was fine; the first screenshot was chainmail over leather.
**How to apply:** any additive pose code (`xRot +=`) on armour of MCA bodies must check PosedCopy.of / posedByMca. Related: [[armour-poses]], [[pose-takeover-emf]], [[cloud-playtest-setup]].
