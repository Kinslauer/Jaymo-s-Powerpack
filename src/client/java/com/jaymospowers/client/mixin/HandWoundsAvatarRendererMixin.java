package com.jaymospowers.client.mixin;

import com.jaymospowers.client.wounds.FirstPersonWounds;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Your own arm in first person: a lost arm is a stump, and the arm's wound marks are drawn over it (FirstPersonWounds). */
@Mixin(AvatarRenderer.class)
abstract class HandWoundsAvatarRendererMixin {
	@Inject(method = "renderHand", at = @At("HEAD"))
	private void jaymospowers$woundsHandBegin(PoseStack poseStack, SubmitNodeCollector collector, int lightCoords, Identifier skinTexture, ModelPart arm,
		boolean hasSleeve, CallbackInfo ci) {
		FirstPersonWounds.begin(arm, ((AvatarRenderer<?>) (Object) this).getModel());
	}

	@Inject(method = "renderHand", at = @At("TAIL"))
	private void jaymospowers$woundsHandEnd(PoseStack poseStack, SubmitNodeCollector collector, int lightCoords, Identifier skinTexture, ModelPart arm,
		boolean hasSleeve, CallbackInfo ci) {
		FirstPersonWounds.end(poseStack, collector, lightCoords, arm, ((AvatarRenderer<?>) (Object) this).getModel());
	}
}
