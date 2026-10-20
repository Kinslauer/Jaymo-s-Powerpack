package com.jaymoskingdoms.client.mixin;

import com.jaymoskingdoms.client.MobAnimations;
import com.jaymoskingdoms.client.PickpocketPose;
import com.jaymoskingdoms.client.PullerPose;
import com.jaymoskingdoms.client.VerdantLook;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Mobs' Better Combat swings (see MobAnimations): the frame's animation goes on the render state, and onto the model as
 * it's posed for the layers (so held items and armour follow the arm).
 */
@Mixin(LivingEntityRenderer.class)
abstract class MobSwingLivingEntityRendererMixin {
	@Inject(
		method = "extractRenderState(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;F)V",
		at = @At("TAIL"))
	private void jaymoskingdoms$swingState(LivingEntity entity, LivingEntityRenderState state, float partialTicks, CallbackInfo ci) {
		MobAnimations.extract(entity, state, partialTicks);
		PullerPose.extract(entity, state, partialTicks);
		PickpocketPose.extract(entity, state, partialTicks);
		VerdantLook.extract(entity, state);
		com.jaymoskingdoms.client.EmberLook.extract(entity, state);
		com.jaymoskingdoms.client.HeritageLooks.extract(entity, state);
		com.jaymoskingdoms.client.VillagerLooks.extract(entity, state);
	}

	@Inject(
		method = "submit(Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V",
		at = @At(value = "INVOKE", target = "Lnet/minecraft/client/model/EntityModel;setupAnim(Ljava/lang/Object;)V", shift = At.Shift.AFTER))
	private void jaymoskingdoms$swingLayers(LivingEntityRenderState state, PoseStack poseStack, SubmitNodeCollector collector,
		CameraRenderState camera, CallbackInfo ci) {
		PullerPose.apply(((LivingEntityRenderer<?, ?, ?>) (Object) this).getModel(), state);
		PickpocketPose.apply(((LivingEntityRenderer<?, ?, ?>) (Object) this).getModel(), state);
		MobAnimations.apply(((LivingEntityRenderer<?, ?, ?>) (Object) this).getModel(), state);
	}
}
