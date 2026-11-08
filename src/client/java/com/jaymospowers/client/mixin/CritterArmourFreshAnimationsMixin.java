package com.jaymospowers.client.mixin;

import com.jaymospowers.client.compat.CritterArmoryFreshAnimationsCompat;
import net.minecraft.client.model.QuadrupedModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Critter Armory's pig and sheep armour models are posed from the render state's vanilla pose after Fresh Animations
 * re-animated the animal model, so the armour slides off the animated body. PoseLayersLivingEntityRendererMixin kept
 * the body's Fresh Animations pose on the render state just before the render layers; here a plain (not Entity Model
 * Features) QuadrupedModel takes it when it is posed from the state, so the armour follows Fresh Animations like it
 * does in vanilla without the pack. Horses are CritterArmourFreshAnimationsEquineMixin. Only acts when both Entity
 * Model Features and Critter Armory are loaded (the state only carries the pose then); Critter Armory is never edited.
 */
@Mixin(QuadrupedModel.class)
abstract class CritterArmourFreshAnimationsMixin {
	@Shadow
	@Final
	protected ModelPart head;
	@Shadow
	@Final
	protected ModelPart body;
	@Shadow
	@Final
	protected ModelPart rightHindLeg;
	@Shadow
	@Final
	protected ModelPart leftHindLeg;
	@Shadow
	@Final
	protected ModelPart rightFrontLeg;
	@Shadow
	@Final
	protected ModelPart leftFrontLeg;

	@Inject(method = "setupAnim(Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;)V", at = @At("TAIL"))
	private void jaymospowers$applyFreshAnimationsPose(LivingEntityRenderState state, CallbackInfo ci) {
		CritterArmoryFreshAnimationsCompat.apply((QuadrupedModel<?>) (Object) this, state, head, body, rightHindLeg, leftHindLeg,
			rightFrontLeg, leftFrontLeg);
	}
}
