package com.jaymoskingdoms.client.mixin;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemUseAnimation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * (0.7.105) A mob holding up its shield ({@code MobShields}) holds it up for watchers too: vanilla only gives players the
 * blocking arm.
 */
@Mixin(HumanoidMobRenderer.class)
abstract class ShieldPoseHumanoidMobRendererMixin {
	@Inject(method = "extractRenderState(Lnet/minecraft/world/entity/Mob;Lnet/minecraft/client/renderer/entity/state/HumanoidRenderState;F)V",
		at = @At("TAIL"))
	private void jaymoskingdoms$shieldUp(Mob mob, HumanoidRenderState state, float partialTicks, CallbackInfo ci) {
		if (!mob.isUsingItem() || mob.getUseItem().getUseAnimation() != ItemUseAnimation.BLOCK) {
			return;
		}
		HumanoidArm arm = mob.getUsedItemHand() == net.minecraft.world.InteractionHand.MAIN_HAND ? mob.getMainArm() : mob.getMainArm().getOpposite();
		if (arm == HumanoidArm.LEFT) {
			state.leftArmPose = HumanoidModel.ArmPose.BLOCK;
		} else {
			state.rightArmPose = HumanoidModel.ArmPose.BLOCK;
		}
	}
}
