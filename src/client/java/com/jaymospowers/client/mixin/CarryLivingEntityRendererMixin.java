package com.jaymospowers.client.mixin;

import com.jaymospowers.client.CarryPose;
import com.jaymospowers.core.Carry;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Carrying (see CarryPose): a held body is moved into the hold, a thrown one tumbles. Late, so the carry wins. */
@Mixin(value = LivingEntityRenderer.class, priority = 2000)
abstract class CarryLivingEntityRendererMixin {
	/** How far above its own box a held body can be drawn (blocks, plus its height). */
	private static final double CULL_RAISE = 2.5;

	@Inject(
		method = "extractRenderState(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;F)V",
		at = @At("TAIL"))
	private void jaymospowers$carryState(LivingEntity entity, LivingEntityRenderState state, float partialTicks, CallbackInfo ci) {
		CarryPose.extract(entity, state, partialTicks);
	}

	/** A held body is drawn above where it stands (up to overhead): don't cull it when only that part is in view. */
	@Inject(method = "getBoundingBoxForCulling(Lnet/minecraft/world/entity/LivingEntity;F)Lnet/minecraft/world/phys/AABB;",
		at = @At("RETURN"), cancellable = true)
	private void jaymospowers$carryCulling(LivingEntity entity, float partialTicks, CallbackInfoReturnable<AABB> cir) {
		if (Carry.isCarried(entity)) {
			cir.setReturnValue(cir.getReturnValue().inflate(1.0).expandTowards(0.0, CULL_RAISE + entity.getBbHeight(), 0.0));
		}
	}

	@Inject(method = "setupRotations", at = @At("TAIL"))
	private void jaymospowers$carryHold(LivingEntityRenderState state, PoseStack poseStack, float bodyRot, float entityScale, CallbackInfo ci) {
		CarryPose.rotate(state, poseStack, entityScale);
	}
}
