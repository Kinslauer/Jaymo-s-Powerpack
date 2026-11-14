package com.jaymospowers.client.rig.mixin;

import com.jaymospowers.client.rig.RigSuit;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Your own arm in first person wears the Rig's Arms: the gauntlet kicks as it fires, and its plates and lights are drawn over the arm (RigSuit). */
@Mixin(AvatarRenderer.class)
abstract class RigHandAvatarRendererMixin {
	@Inject(method = "renderHand", at = @At("HEAD"))
	private void jaymospowers$rigHandKick(PoseStack poseStack, SubmitNodeCollector collector, int lightCoords, Identifier skinTexture, ModelPart arm,
		boolean hasSleeve, CallbackInfo ci) {
		RigSuit.kickHand(poseStack);
	}

	@Inject(method = "renderHand", at = @At("TAIL"))
	private void jaymospowers$rigHand(PoseStack poseStack, SubmitNodeCollector collector, int lightCoords, Identifier skinTexture, ModelPart arm,
		boolean hasSleeve, CallbackInfo ci) {
		RigSuit.renderHand(poseStack, collector, lightCoords, arm, ((AvatarRenderer<?>)(Object)this).getModel());
	}
}
