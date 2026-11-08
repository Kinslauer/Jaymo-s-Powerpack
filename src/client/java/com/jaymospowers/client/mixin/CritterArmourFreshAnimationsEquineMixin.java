package com.jaymospowers.client.mixin;

import com.jaymospowers.client.compat.CritterArmoryFreshAnimationsCompat;
import net.minecraft.client.model.animal.equine.AbstractEquineModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.entity.state.EquineRenderState;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The horse version of CritterArmourFreshAnimationsMixin: horse armour (Critter Armory's included, which only adds
 * armour items to the vanilla horse body armour layer) is drawn by its own HorseModel, posed from the render state, so
 * a plain AbstractEquineModel (horse, donkey, mule, and the saddle model) takes the pose Fresh Animations gave the head
 * and neck, body and legs, kept on the state by PoseLayersLivingEntityRendererMixin. The tail is not followed.
 */
@Mixin(AbstractEquineModel.class)
abstract class CritterArmourFreshAnimationsEquineMixin {
	@Shadow
	@Final
	protected ModelPart headParts;
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

	@Inject(method = "setupAnim(Lnet/minecraft/client/renderer/entity/state/EquineRenderState;)V", at = @At("TAIL"))
	private void jaymospowers$applyFreshAnimationsPose(EquineRenderState state, CallbackInfo ci) {
		CritterArmoryFreshAnimationsCompat.apply((AbstractEquineModel<?>) (Object) this, state, headParts, body, rightHindLeg, leftHindLeg,
			rightFrontLeg, leftFrontLeg);
	}
}
