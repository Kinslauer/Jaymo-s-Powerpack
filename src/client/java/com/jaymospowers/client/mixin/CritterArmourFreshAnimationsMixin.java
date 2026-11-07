package com.jaymospowers.client.mixin;

import com.jaymospowers.client.compat.CritterArmoryFreshAnimationsCompat;
import net.minecraft.client.model.QuadrupedModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Critter Armory's armour models are posed from the render state's vanilla pose after Fresh Animations re-animated
 * the animal model, so the armour slides off the animated body. PoseLayersLivingEntityRendererMixin kept the body's
 * Fresh Animations pose on the render state just before the render layers; here Critter Armory's armour model takes
 * it when it is posed from the state, so the armour follows Fresh Animations like it does in vanilla without the pack.
 * Only active when both Entity Model Features and Critter Armory are loaded; Critter Armory is linked, never edited.
 */
@Mixin(QuadrupedModel.class)
abstract class CritterArmourFreshAnimationsMixin {
	@Shadow
	protected ModelPart head;
	@Shadow
	protected ModelPart body;
	@Shadow
	protected ModelPart rightHindLeg;
	@Shadow
	protected ModelPart leftHindLeg;
	@Shadow
	protected ModelPart rightFrontLeg;
	@Shadow
	protected ModelPart leftFrontLeg;

	@Inject(method = "setupAnim(Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;)V", at = @At("TAIL"))
	private void jaymospowers$applyFreshAnimationsPose(LivingEntityRenderState state, CallbackInfo ci) {
		PartPose[] poses = state.getData(CritterArmoryFreshAnimationsCompat.CRITTER_ARMOR_EMF_POSES);
		if (poses == null || poses.length != 6) {
			return;
		}
		// The body's Fresh Animations pose, kept on the state by PoseLayersLivingEntityRendererMixin: take it, so the
		// armour model follows the animated body instead of the vanilla pose captured before Fresh Animations ran.
		head.loadPose(poses[0]);
		body.loadPose(poses[1]);
		rightHindLeg.loadPose(poses[2]);
		leftHindLeg.loadPose(poses[3]);
		rightFrontLeg.loadPose(poses[4]);
		leftFrontLeg.loadPose(poses[5]);
	}
}
