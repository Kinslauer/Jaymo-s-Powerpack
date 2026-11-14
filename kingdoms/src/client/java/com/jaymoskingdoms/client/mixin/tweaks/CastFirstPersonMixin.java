package com.jaymoskingdoms.client.mixin.tweaks;

import com.jaymoskingdoms.client.CastPose;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.FirstPersonHandsAndItemsRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.state.level.FirstPersonHandsAndItemsRenderState;
import net.minecraft.client.renderer.state.level.PlayerRenderState;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;

/** First person: the hand you cast with makes its gesture ({@link CastPose#firstPerson}) before it is drawn, whatever it holds. */
@Mixin(FirstPersonHandsAndItemsRenderer.class)
abstract class CastFirstPersonMixin {
	@WrapMethod(method = "submitArmWithItem")
	private void jaymoskingdoms$castHand(PlayerRenderState playerState, FirstPersonHandsAndItemsRenderState state, float partialTicks, float xRot,
		InteractionHand hand, float attack, ItemStack itemStack, float inverseArmHeight, PoseStack poseStack, SubmitNodeCollector submitNodeCollector,
		int lightCoords, Operation<Void> original) {
		poseStack.pushPose();
		CastPose.firstPerson(poseStack, hand, playerState.avatarRenderState == null ? HumanoidArm.RIGHT : playerState.avatarRenderState.mainArm, partialTicks);
		original.call(playerState, state, partialTicks, xRot, hand, attack, itemStack, inverseArmHeight, poseStack, submitNodeCollector, lightCoords);
		poseStack.popPose();
	}
}
