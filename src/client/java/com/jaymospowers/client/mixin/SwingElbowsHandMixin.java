package com.jaymospowers.client.mixin;

import com.jaymospowers.client.combat.SwingElbows;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.world.entity.HumanoidArm;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** What a player holds follows a forearm that a Better Combat animation folds at the elbow (see SwingElbows). */
@Mixin(PlayerModel.class)
abstract class SwingElbowsHandMixin {
	@Inject(method = "translateToHand(Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;Lnet/minecraft/world/entity/HumanoidArm;Lcom/mojang/blaze3d/vertex/PoseStack;)V",
		at = @At("TAIL"))
	private void jaymospowers$swingElbow(AvatarRenderState state, HumanoidArm arm, PoseStack poseStack, CallbackInfo ci) {
		SwingElbows.hand(state, arm, poseStack);
	}
}
