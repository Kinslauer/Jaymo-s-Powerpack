---
name: armour-poses
description: Why Armory 3D pieces/wizard hats clipped (not HumanoidModels, no EMF pose); WornModel fix PR #223 (pack 0.11.18 / Kingdoms 0.7.48)
metadata:
  type: project
  modified: 2026-10-15T03:12:52.364Z
---
2026-10-15 Andy: "some armors clip through, probably aren't animated correctly". PR #223 MERGED (branch claude/project-thread-ygdr67, pack 0.11.18 / Kingdoms 0.7.48), jars /mnt/project-files/armour-poses/. Not playtested with Powers poses.
- Cause: ArmoryLayer/SorcerySkins pieces were plain Model copying parent vanilla setupAnim; pose mixins at ModelFeatureRenderer.prepareModel only touch HumanoidModel, and EMF only re-applies Fresh Animations' pose to PlayerModel (player armour models ARE PlayerModels, so vanilla armour already follows FA).
- Fix: kingdoms client/WornModel (HumanoidModel subclass) + ArmorPoseLivingEntityRendererMixin keeps the EMF-animated body pose on the render state before layers (EmfWornPose: only when EMF animated it, not paused).
**How to apply:** any new layer model worn over a body should extend WornModel. Verified in a cloud test game (Kingdoms dev + temporary localRuntime EMF/ETF + FA zip in run/resourcepacks); see [[cloud-playtest-setup]], [[pose-takeover-emf]].
