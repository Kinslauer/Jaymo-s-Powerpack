package com.jaymospowers.client.mixin;

import com.jaymospowers.client.FirstPersonArms;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.FirstPersonHandsAndItemsRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.state.level.FirstPersonHandsAndItemsRenderState;
import net.minecraft.client.renderer.state.level.PlayerRenderState;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * (0.15.40) First person, each hand: the big moves pose your own bare arm (FirstPersonArms: Kindle, Time Flow, Lift,
 * Cryokinesis), only where the hand is empty; and while the carry arms come in or go (CarryFirstPersonMixin), what the
 * hands hold sinks out of view or rises back the way vanilla's own equip does (the hand drawn again at that height).
 */
@Mixin(FirstPersonHandsAndItemsRenderer.class)
abstract class PowerArmsFirstPersonMixin {
	@Unique
	private boolean jaymospowers$resubmitting;

	@Inject(method = "submitArmWithItem", at = @At("HEAD"), cancellable = true)
	private void jaymospowers$powerArms(PlayerRenderState playerState, FirstPersonHandsAndItemsRenderState state, float partialTicks,
		float xRot, InteractionHand hand, float attack, ItemStack itemStack, float inverseArmHeight, PoseStack poseStack,
		SubmitNodeCollector collector, int lightCoords, CallbackInfo ci) {
		if (this.jaymospowers$resubmitting) {
			return;
		}
		float sink = FirstPersonArms.carrySink();
		if (sink > inverseArmHeight + 0.001F) {
			// the carry arms coming in or going: this hand lowered as far as they're in
			this.jaymospowers$resubmitting = true;
			try {
				((CarryArmInvoker)this).jaymospowers$submitArmWithItem(playerState, state, partialTicks, xRot, hand, attack, itemStack,
					Math.min(1.0F, sink), poseStack, collector, lightCoords);
			} finally {
				this.jaymospowers$resubmitting = false;
			}
			ci.cancel();
			return;
		}
		AvatarRenderState avatar = playerState.avatarRenderState;
		if (state.isScoping || avatar == null || avatar.isInvisible) {
			return;
		}
		boolean main = hand == InteractionHand.MAIN_HAND;
		HumanoidArm arm = main ? avatar.mainArm : avatar.mainArm.getOpposite();
		poseStack.pushPose();
		float weight = FirstPersonArms.pose(poseStack, avatar, main, arm == HumanoidArm.RIGHT, itemStack.isEmpty(), partialTicks);
		if (weight < 0.0F) {
			poseStack.popPose();
			return;
		}
		// vanilla's own swing fades out while a move has the arm
		((CarryArmInvoker)this).jaymospowers$renderPlayerArm(poseStack, collector, lightCoords, inverseArmHeight,
			main ? attack * (1.0F - weight) : 0.0F, arm, playerState);
		poseStack.popPose();
		ci.cancel();
	}
}
