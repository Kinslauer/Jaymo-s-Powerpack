package com.jaymospowers.client.mixin;

import com.jaymospowers.client.costume.CostumeClient;
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

/**
 * (0.11.66) A worn costume goes on the player's render state last, after every power has dressed it, and takes the
 * armour off it (see CostumeClient); in first person, the costume's sleeve goes over your hand.
 */
@Mixin(value = AvatarRenderer.class, priority = 2100)
abstract class CostumeAvatarRendererMixin {
	@Inject(
		method = "extractRenderState(Lnet/minecraft/world/entity/Avatar;Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;F)V",
		at = @At("TAIL"))
	private void jaymospowers$costume(Avatar entity, AvatarRenderState state, float partialTicks, CallbackInfo ci) {
		CostumeClient.extract(entity, state);
	}

	@Inject(method = "renderHand", at = @At("TAIL"))
	private void jaymospowers$costumeHand(PoseStack poseStack, SubmitNodeCollector collector, int lightCoords, Identifier skinTexture, ModelPart arm,
		boolean hasSleeve, CallbackInfo ci) {
		CostumeClient.renderHand(poseStack, collector, lightCoords, arm, ((AvatarRenderer<?>)(Object)this).getModel());
	}
}
