package com.jaymoskingdoms.client.mixin;

import com.jaymoskingdoms.client.SpeechBubbles;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Speech over any entity's head (see SpeechBubbles), and (0.5.28) quest givers' marks: put on the render state, then drawn after the entity. */
@Mixin(EntityRenderDispatcher.class)
abstract class SpeechEntityRenderDispatcherMixin {
	@Inject(method = "extractEntity", at = @At("RETURN"))
	private void jaymoskingdoms$speechState(Entity entity, float partialTicks, CallbackInfoReturnable<EntityRenderState> cir) {
		SpeechBubbles.extract(entity, cir.getReturnValue(), partialTicks);
		com.jaymoskingdoms.client.QuestClient.extract(entity, cir.getReturnValue(), partialTicks);
	}

	@Inject(method = "submit", at = @At(value = "INVOKE",
		target = "Lnet/minecraft/client/renderer/entity/EntityRenderer;submit(Lnet/minecraft/client/renderer/entity/state/EntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V",
		shift = At.Shift.AFTER))
	private void jaymoskingdoms$speech(EntityRenderState state, CameraRenderState camera, double x, double y, double z, PoseStack poseStack,
		SubmitNodeCollector collector, CallbackInfo ci) {
		SpeechBubbles.submit(state, poseStack, collector, camera);
		com.jaymoskingdoms.client.QuestClient.submit(state, poseStack, collector, camera);
	}
}
