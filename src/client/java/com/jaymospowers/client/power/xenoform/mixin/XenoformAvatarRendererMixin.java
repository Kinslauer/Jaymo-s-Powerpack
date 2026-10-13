package com.jaymospowers.client.power.xenoform.mixin;

import com.jaymospowers.client.power.xenoform.XenoformClient;
import com.jaymospowers.client.power.xenoform.XenoformHand;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Avatar;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** A transformed player is drawn as their alien: the render state is marked (and the body hidden), and in first person the alien's arm replaces yours. */
@Mixin(AvatarRenderer.class)
abstract class XenoformAvatarRendererMixin {
	@Inject(
		method = "extractRenderState(Lnet/minecraft/world/entity/Avatar;Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;F)V",
		at = @At("TAIL"))
	private void jaymospowers$xenoform(Avatar entity, AvatarRenderState state, float partialTicks, CallbackInfo ci) {
		XenoformClient.extract(entity, state, partialTicks);
	}

	@Inject(method = "renderHand", at = @At("HEAD"), cancellable = true)
	private void jaymospowers$xenoformHand(PoseStack poseStack, SubmitNodeCollector collector, int lightCoords, Identifier skinTexture, ModelPart arm,
		boolean hasSleeve, CallbackInfo ci) {
		if (XenoformHand.render(poseStack, collector, lightCoords, arm, ((AvatarRenderer<?>)(Object)this).getModel())) {
			ci.cancel();
		}
	}
}
