---
name: pose-takeover-emf
description: Why our poses broke under Fresh Animations/EMF and holstered items walked mid-air; fixes in PR #185 (pack 0.10.86), plus Xeno Z+scroll dial and dragon-wing Wyrm
metadata:
  type: project
---
2026-10-14 Andy: flight poses (Z Fighter, Web wings) broken, holstered items walk in mid-air, Xeno-Band first person broken, wants Z+scroll dial / release slam, Wyrm wings as small ender dragon wings. PR #185 MERGED 2026-10-14 22:06Z (branch claude/project-thread-doeayy, pack 0.10.86, jar /mnt/project-files/animation-fixes/). Not playtested.
- EMF animates models AGAIN at ModelPart.render (EMFModelPartWithState.render -> root.animate), after our prepareModel poses; its pause asks per entity, so PoseTakeover keeps posed entity ids (posedEntity). drawingPosed alone is false then.
- LivingEntityRenderer.submit calls model.setupAnim before layers; layers (YDM Weapon Master holsters, XenoBand) read that. ModelPoses.apply runs every applyModel there too (PoseLayersLivingEntityRendererMixin). **How to apply:** a new pose's applyModel goes in ModelPoses as well as its power's ModelFeatureRendererMixin; flight keys also go in PoseTakeover.flying (walk zeroed).
- WyrmWings: vanilla EnderDragonModel wing boxes at 0.2 scale, game's own dragon.png by name.
