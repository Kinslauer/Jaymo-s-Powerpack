---
status: done
task: critter-armory-fit
branch: claude/critter-armory-fit
with:
pr: 1125
mods: Powers
versions: 0.21.56
started: 2026-11-07
owns:
  - src/client/java/com/jaymospowers/client/mixin/CritterArmourFreshAnimationsMixin.java
  - src/client/java/com/jaymospowers/client/mixin/PoseLayersLivingEntityRendererMixin.java
  - src/client/java/com/jaymospowers/client/EmfAccess.java
  - MODLOG.md
---

# Critter Armory armour sits wrong on animals

**Task:** Critter Armory's animal armour (added unchanged in Powers 0.20.85) is misplaced on pigs, sheep, and horses. Find whether our own animal models or render changes move body parts the armour layer copies, and fix from our side (compat class or our own models' part names/poses).

## Plan
- Search codebase for pig/sheep/horse model and renderer changes
- Check if Kitchen corpses/ragdolls or other mods remodel animal body parts
- Identify the misplacement cause
- Patch and build-check

## Notes
**Investigation findings:**
- Critter Armory 0.6.5 added in Powers 0.20.85 (MODLOG line 683-689), shipped unchanged, never play-tested
- Critter Armory uses mixins on PigRenderer/SheepRenderer (adds PigArmorLayer/SheepArmorLayer), HorseMixin (entity class), EquipmentLayerRendererMixin
- PigArmorLayer/SheepArmorLayer bake their own armor models (PIG_ARMOR/SHEEP_ARMOR layers) and call CommonUtils.renderArmor(ResourceKey) to position armor relative to animal model parts
- Our code does NOT modify animal model parts for pigs/sheep/horses:
  - All Powers pose methods check for AvatarRenderState/HumanoidModel (player-only) — verified by reading each applyModel method
  - Kitchen's Limp.apply only affects carried/dead entities
  - Kitchen's CarryPose only affects AvatarRenderState/HumanoidModel (players)
  - Kingdoms' AbstractHorseRendererMixin only extracts render state data
  - No mixins on PigModel/SheepModel/HorseModel in our codebase
  - No custom entity renderers for pig/sheep/horse in Powers or Kitchen
- Fresh Animations (EMF resource pack, PackResourcePacks.java line 89) is installed and changes models of "most vanilla mobs" including pigs, sheep, horses — this changes model part positions, which would cause Critter Armory's armor (positioned relative to those parts) to be misplaced
- Fresh Animations is NOT downloaded in the dev environment (not in modpack/resourcepacks/), so the issue can't be reproduced or tested here

**Conclusion:** Our code is NOT the cause. Fresh Animations changing model geometry is the most likely cause. A code-side compat fix would need to know exactly what Fresh Animations changes to each model part, which can't be determined without inspecting the Fresh Animations resource pack.

**Question for Andy:** Is Fresh Animations active in your game? Do you want us to create a compatibility fix for Critter Armory + Fresh Animations, or should we take a different approach (e.g., disabling Fresh Animations for pigs/sheep/horses)?

## Done
What a player will see, and what wasn't tested.
- 2026-11-08 12:44 PR #1125 is merged into master; moved to Agents/Done.
