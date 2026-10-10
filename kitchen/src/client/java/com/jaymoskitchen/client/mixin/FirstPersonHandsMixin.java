package com.jaymoskitchen.client.mixin;

import com.jaymoskitchen.corpse.CorpseEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.FirstPersonHandsAndItemsRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.state.level.FirstPersonHandsAndItemsRenderState;
import net.minecraft.client.renderer.state.level.PlayerRenderState;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Carrying a corpse in first person: both arms out in front under it (as when holding a map in both hands). */
@Mixin(FirstPersonHandsAndItemsRenderer.class)
abstract class FirstPersonHandsMixin {
	@Shadow
	private void renderMapHand(PoseStack poseStack, SubmitNodeCollector collector, int lightCoords, HumanoidArm arm,
		PlayerRenderState playerState) {
	}

	@Inject(method = "submitArmWithItem", at = @At("HEAD"), cancellable = true)
	private void jaymoskitchen$carrying(PlayerRenderState playerState, FirstPersonHandsAndItemsRenderState state, float partialTicks,
		float xRot, InteractionHand hand, float attack, ItemStack itemStack, float inverseArmHeight, PoseStack poseStack,
		SubmitNodeCollector collector, int lightCoords, CallbackInfo ci) {
		Player player = Minecraft.getInstance().player;
		if (player == null || CorpseEntity.carriedBy(player) == null) {
			return;
		}
		ci.cancel();
		AvatarRenderState avatar = playerState.avatarRenderState;
		if (hand != InteractionHand.MAIN_HAND || avatar == null || avatar.isInvisible) {
			return;
		}
		poseStack.pushPose();
		poseStack.translate(0.0F, 0.04F + inverseArmHeight * -1.2F, -0.72F);
		poseStack.rotateDegrees(Axis.YP, 90.0F);
		renderMapHand(poseStack, collector, lightCoords, HumanoidArm.RIGHT, playerState);
		renderMapHand(poseStack, collector, lightCoords, HumanoidArm.LEFT, playerState);
		poseStack.popPose();
	}
}
