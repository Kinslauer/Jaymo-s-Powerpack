---
name: herd-killings
description: Herd animals flee nearby killings/downings (Kingdoms Herds.killing), PR #983, Kingdoms 0.7.560, hint 102
metadata:
  type: project
  modified: 2026-11-05T07:11:45.808Z
---
2026-11-05 Andy: "passive animals should run when someone is killed near them" (video of a pen beside an orc ambush).
PR #983 MERGED (branch claude/project-thread-bbjxzo), Kingdoms 0.7.560, hint 102 (lang part herd-killings.json), built, not played.
`Herds.killing`: AFTER_DEATH or AFTER_DAMAGE on a downed body (non-skittish victims) -> skittish animals within 16 (seen) / 6 (unseen) flee killer or body 4 s. Ridden and tamed stay calm; leashed/named still panic.
Defaults chosen by Claude, not Andy: ranges, animals dying don't trigger it (kin panic already does).
Same PR, Kitchen 0.5.68 (Andy 07:14Z "deer clip into ground as corpses, new mobs don't close eyes"): RagdollPose.stands drives modded quadrupeds' *leg* parts + head (Cube Animals legs in groups/90° wrappers hung loose through the ground); ClosedEyes judges head faces in model space, head any case, subtree parts. MERGED 2026-11-05 07:3xZ as Kingdoms 0.7.564 / Kitchen 0.5.68, hint 103. Jars /mnt/project-files/herd-killings/.
