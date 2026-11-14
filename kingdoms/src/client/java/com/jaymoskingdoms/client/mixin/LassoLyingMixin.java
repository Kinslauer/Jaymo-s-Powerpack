package com.jaymoskingdoms.client.mixin;

import com.jaymoskingdoms.client.forms.LassoPose;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Someone hogtied by a lasso is laid on their side (LassoPose). */
@Mixin(LivingEntityRenderer.class)
abstract class LassoLyingMixin {
	@Inject(method = "setupRotations", at = @At("TAIL"))
	private void jaymoskingdoms$hogtiedLying(LivingEntityRenderState state, PoseStack poseStack, float bodyRot, float entityScale, CallbackInfo ci) {
		LassoPose.rotate(state, poseStack, entityScale);
	}
}
