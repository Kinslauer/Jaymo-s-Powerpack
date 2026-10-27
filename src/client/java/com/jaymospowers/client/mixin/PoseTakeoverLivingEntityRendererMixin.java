package com.jaymospowers.client.mixin;

import com.jaymospowers.client.PoseTakeover;
import com.jaymospowers.client.TnmFollow;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Which living entity is being drawn, so other mods' animation checks can see our poses on it (see PoseTakeover). */
@Mixin(LivingEntityRenderer.class)
abstract class PoseTakeoverLivingEntityRendererMixin {
	private static final String SUBMIT = "submit(Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;"
		+ "Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V";

	@Shadow
	protected EntityModel<?> model;

	@Inject(method = SUBMIT, at = @At("HEAD"))
	private void jaymospowers$beginDraw(LivingEntityRenderState state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera,
		CallbackInfo ci) {
		PoseTakeover.beginDraw(state);
	}

	/** (0.17.40) The body as posed for the layers (after any model swap, EMF's animation and Touch'n Motion's): see TnmFollow. */
	@Inject(method = SUBMIT, at = @At(value = "INVOKE", target = "Ljava/util/List;iterator()Ljava/util/Iterator;"))
	private void jaymospowers$keepBody(LivingEntityRenderState state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera,
		CallbackInfo ci) {
		TnmFollow.keep(state, this.model);
	}

	@Inject(method = SUBMIT, at = @At("RETURN"))
	private void jaymospowers$endDraw(LivingEntityRenderState state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera,
		CallbackInfo ci) {
		PoseTakeover.endDraw();
	}
}
