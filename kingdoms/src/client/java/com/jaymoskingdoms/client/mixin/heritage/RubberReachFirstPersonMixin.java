package com.jaymoskingdoms.client.mixin.heritage;

import com.jaymoskingdoms.client.RubberEngulf;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.FirstPersonHandsAndItemsRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.state.level.FirstPersonHandsAndItemsRenderState;
import net.minecraft.client.renderer.state.level.PlayerRenderState;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** (0.7.421) First person, a Rubber-folk swallowing: your bare main arm reaches out ahead, and the rope leaves its fist (see RubberEngulf). */
@Mixin(FirstPersonHandsAndItemsRenderer.class)
abstract class RubberReachFirstPersonMixin {
	@Shadow
	private void renderPlayerArm(PoseStack poseStack, SubmitNodeCollector submitNodeCollector, int lightCoords, float inverseArmHeight,
		float attackValue, net.minecraft.world.entity.HumanoidArm arm, PlayerRenderState playerState) {
	}

	@Inject(method = "submitArmWithItem", at = @At("HEAD"), cancellable = true)
	private void jaymoskingdoms$rubberReach(PlayerRenderState playerState, FirstPersonHandsAndItemsRenderState state, float partialTicks, float xRot,
		InteractionHand hand, float attack, ItemStack itemStack, float inverseArmHeight, PoseStack poseStack, SubmitNodeCollector submitNodeCollector,
		int lightCoords, CallbackInfo ci) {
		AvatarRenderState avatar = playerState.avatarRenderState;
		if (state.isScoping || avatar == null) {
			return;
		}
		poseStack.pushPose();
		if (RubberEngulf.firstPersonReach(poseStack, avatar, hand) <= 0.0F) {
			poseStack.popPose();
			return;
		}
		RubberEngulf.reaching(true);
		// no swing, and no lowering as the held item changes: the arm stays out on the foe
		this.renderPlayerArm(poseStack, submitNodeCollector, lightCoords, 0.0F, 0.0F, avatar.mainArm, playerState);
		RubberEngulf.reaching(false);
		poseStack.popPose();
		ci.cancel();
	}
}
