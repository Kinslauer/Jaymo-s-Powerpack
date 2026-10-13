package com.jaymospowers.client.power.darktide.mixin;

import com.jaymospowers.client.power.darktide.DarktidePose;
import com.jaymospowers.power.darktide.DarktidePower;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Darktide's poses read onto the render state; a crippled mob laid on the ground; risen shadows drawn dark; a veiled user's name tag hidden in the dark. */
@Mixin(LivingEntityRenderer.class)
abstract class DarktideLivingEntityRendererMixin {
	@Inject(
		method = "extractRenderState(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;F)V",
		at = @At("TAIL"))
	private void jaymospowers$darktidePose(LivingEntity entity, LivingEntityRenderState state, float partialTicks, CallbackInfo ci) {
		DarktidePose.extract(entity, state, partialTicks);
	}

	/**
	 * The held-item layers take the hands from the model as it's animated here, before the body is drawn: the pose goes
	 * on now too, so a raised blade (or twin daggers) rises with the hands.
	 */
	@Inject(
		method = "submit(Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V",
		at = @At(value = "INVOKE", target = "Lnet/minecraft/client/model/EntityModel;setupAnim(Ljava/lang/Object;)V", shift = At.Shift.AFTER))
	private void jaymospowers$darktideHands(LivingEntityRenderState state, PoseStack poseStack, net.minecraft.client.renderer.SubmitNodeCollector collector,
		net.minecraft.client.renderer.state.level.CameraRenderState camera, CallbackInfo ci) {
		DarktidePose.applyModel(((LivingEntityRenderer<?, ?, ?>) (Object) this).getModel(), state);
	}

	@Inject(method = "setupRotations", at = @At("TAIL"))
	private void jaymospowers$darktideCrawl(LivingEntityRenderState state, PoseStack poseStack, float bodyRot, float entityScale, CallbackInfo ci) {
		DarktidePose.rotateCrawl(state, poseStack);
	}

	@Inject(method = "getModelTint", at = @At("RETURN"), cancellable = true)
	private void jaymospowers$darktideRisen(LivingEntityRenderState state, CallbackInfoReturnable<Integer> cir) {
		int tint = DarktidePose.tint(state, cir.getReturnValue());
		if (tint != cir.getReturnValue()) {
			cir.setReturnValue(tint);
		}
	}

	@Inject(method = "shouldShowName(Lnet/minecraft/world/entity/LivingEntity;D)Z", at = @At("HEAD"), cancellable = true)
	private void jaymospowers$darktideVeil(LivingEntity entity, double distanceSq, CallbackInfoReturnable<Boolean> cir) {
		if (entity instanceof Player player && DarktidePower.veilHidesName(player)) {
			cir.setReturnValue(false);
		}
	}
}
