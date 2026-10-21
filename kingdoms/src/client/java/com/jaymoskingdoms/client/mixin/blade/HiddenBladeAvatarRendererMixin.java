package com.jaymoskingdoms.client.mixin.blade;

import com.jaymoskingdoms.client.HiddenBladeClient;
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

/** (0.7.113) A player's hidden blades onto their render state, and onto your own first-person hand (see HiddenBladeClient). */
@Mixin(AvatarRenderer.class)
abstract class HiddenBladeAvatarRendererMixin {
	@Inject(
		method = "extractRenderState(Lnet/minecraft/world/entity/Avatar;Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;F)V",
		at = @At("TAIL"))
	private void jaymoskingdoms$bladeState(Avatar entity, AvatarRenderState state, float partialTicks, CallbackInfo ci) {
		HiddenBladeClient.extract(entity, state, partialTicks);
	}

	@Inject(method = "renderHand", at = @At("TAIL"))
	private void jaymoskingdoms$bladeHand(PoseStack poseStack, SubmitNodeCollector collector, int lightCoords, Identifier skinTexture, ModelPart arm,
		boolean hasSleeve, CallbackInfo ci) {
		HiddenBladeClient.firstPersonHand(poseStack, collector, lightCoords, arm, ((AvatarRenderer<?>)(Object)this).getModel());
	}
}
