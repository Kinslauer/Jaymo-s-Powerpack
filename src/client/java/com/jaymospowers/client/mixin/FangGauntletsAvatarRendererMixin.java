package com.jaymospowers.client.mixin;

import com.jaymospowers.client.armory.FangGauntletsClient;
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

/** (0.18.74) Fang Gauntlets worn: onto the render state, and onto your own first-person hands (see FangGauntletsClient). */
@Mixin(AvatarRenderer.class)
abstract class FangGauntletsAvatarRendererMixin {
	@Inject(
		method = "extractRenderState(Lnet/minecraft/world/entity/Avatar;Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;F)V",
		at = @At("TAIL"))
	private void jaymospowers$gauntletState(Avatar entity, AvatarRenderState state, float partialTicks, CallbackInfo ci) {
		FangGauntletsClient.extract(entity, state, partialTicks);
	}

	@Inject(method = "renderHand", at = @At("TAIL"))
	private void jaymospowers$gauntletHand(PoseStack poseStack, SubmitNodeCollector collector, int lightCoords, Identifier skinTexture, ModelPart arm,
		boolean hasSleeve, CallbackInfo ci) {
		FangGauntletsClient.firstPersonHand(poseStack, collector, lightCoords, arm, ((AvatarRenderer<?>) (Object) this).getModel());
	}
}
