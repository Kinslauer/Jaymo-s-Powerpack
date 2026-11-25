package com.jaymospowers.client.power.wind.mixin;

import com.jaymospowers.client.power.wind.WindPose;
import com.jaymospowers.power.wind.WindElectro;
import com.mojang.blaze3d.vertex.PoseStack;
import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Tips a cruising player's body into the flight direction (see WindPose). A charcoal statue (WindElectro#CHARRED) stands
 * where it died, unmoving and black: no topple, no red, no breathing.
 */
@Mixin(LivingEntityRenderer.class)
abstract class LivingEntityRendererMixin {
	/** The charcoal a statue is drawn in (ARGB). */
	@Unique
	private static final int CHARCOAL = 0xFF2C2624;
	@Unique
	private static final RenderStateDataKey<Boolean> CHARRED = RenderStateDataKey.create(() -> "jaymospowers:charred");

	@Inject(method = "setupRotations", at = @At("TAIL"))
	private void jaymospowers$windFlightPose(LivingEntityRenderState state, PoseStack poseStack, float bodyRot, float entityScale, CallbackInfo ci) {
		WindPose.rotate(state, poseStack, entityScale);
	}

	@Inject(
		method = "extractRenderState(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;F)V",
		at = @At("TAIL"))
	private void jaymospowers$charredStatue(LivingEntity entity, LivingEntityRenderState state, float partialTicks, CallbackInfo ci) {
		if (!entity.hasAttached(WindElectro.CHARRED)) {
			state.setData(CHARRED, null);
			return;
		}
		state.setData(CHARRED, Boolean.TRUE);
		state.deathTime = 0.0F;
		state.hasRedOverlay = false;
		state.walkAnimationSpeed = 0.0F;
		state.ageInTicks = 0.0F;
	}

	@Inject(method = "getModelTint", at = @At("RETURN"), cancellable = true)
	private void jaymospowers$charcoal(LivingEntityRenderState state, CallbackInfoReturnable<Integer> cir) {
		if (state.getData(CHARRED) != null) {
			cir.setReturnValue(CHARCOAL);
		}
	}
}
