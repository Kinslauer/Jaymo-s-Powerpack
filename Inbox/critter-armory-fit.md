---
status: in-review
for: any
from: coordinator
asked: 2026-11-07
thread: Claude project thread "Fix new armour mapping on mobs" (never started, thread ran out of usage)
claimed_by: hermes-b
pr: 1125
suggested_owns:
  - 
---
# Critter Armory armour sits wrong on animals

**Andy's words (2026-11-07):** "the new armor isnt mapped properly to the mobs".

**What's wanted:** Critter Armory's animal armour (added unchanged in Powers 0.20.85) fits pigs, sheep and horses the
way it does in plain Minecraft. Andy's two clips (in the "Fewer water mobs" request) show it misplaced on all three.

**Where to start:** find out first whether our own animal models or render changes (Kitchen corpses/ragdolls, any
remodelled pig/sheep/horse, Fresh-Animations-style packs in the pack) move the body parts the armour layer copies.
Critter Armory is ARR unless its licence says otherwise, so fix it from our side (compat class, or our own models'
part names/poses), not by copying its code.

## Notes
- Cause not known yet.
- 2026-11-07 17:36 Coordinator: raised the Fresh Animations question with Andy 2026-11-07 17:37.
- 2026-11-07 17:42 Andy (2026-11-07 17:42) chose: build a compat fix so Critter Armory's armour fits Fresh Animations' pig, sheep and horse models (not turning Fresh Animations off, not dropping Critter Armory). Fresh Animations is an EMF/ETF resource pack: get its .jem files for pig/sheep/horse (download the pack the way PackResourcePacks does) and offset the armour layers to match, through a compat class that checks both are loaded.
- 2026-11-07 17:42 Coordinator hint from past fixes: EMF re-animates models at ModelPart.render, after layers copied the parent pose (see memory/PR #185 PoseTakeover.posedEntity, PR #223 kingdoms client/WornModel + ArmorPoseLivingEntityRendererMixin/EmfWornPose that keep the EMF-animated pose on the render state before layers). Likely the same fix shape: carry FA's animated pig/sheep/horse part poses into Critter Armory's PigArmorLayer/SheepArmorLayer/horse equipment layer, or pause EMF for armoured animals. Test with a temporary localRuntime EMF/ETF + FA zip in run/resourcepacks (cloud-playtest-setup).
- 2026-11-07 17:19 hermes-b's last run stopped before finishing; handed back as open so it can carry on.
- 2026-11-07 17:53 Review (head a36cb84): changes needed (first review). Good: a compat class that only acts when Entity Model Features and Critter Armory are both loaded; the body's animated pose is kept on the render state before the layers (PoseLayersLivingEntityRendererMixin) and loaded into the armour models' parts (CritterArmourFreshAnimationsMixin), the same shape as Kingdoms' WornModel/EmfWornPose; Critter Armory's PigArmorLayer/SheepArmorLayer draw with vanilla PigModel/SheepModel, which are QuadrupedModels, so pigs and sheep are covered; Powers 0.21.56 and a MODLOG entry; checks green. To do: 1) Horses aren't covered, and Andy named them (pig, sheep and horse): both mixins only act on QuadrupedModel (PoseLayersLivingEntityRendererMixin's instanceof check, CritterArmourFreshAnimationsMixin's target), but in 26.3 HorseModel extends AbstractEquineModel extends EntityModel, not QuadrupedModel, and Critter Armory's horse armour comes through its HorseMixin/EquipmentLayerRendererMixin. Do the same for AbstractEquineModel: capture its body, head and leg parts (check the 26.3 field names) and load them into the model the horse armour layer poses from. 2) Like EmfWornPose.animatedNow, only keep the captured pose when EMF actually animated that body this frame (EMFModelPartRoot.hasAnimation and not paused), so Powers' own poses and paused animations keep working. 3) Small: MODLOG.md has two blank lines before the new heading. Then build, pr_checks, push, set status=in-review.
