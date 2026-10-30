package com.jaymoskingdoms.client.mixin;

import com.jaymoskingdoms.client.ValorLook;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** (0.7.408) Your Valor coat over your own first-person arm (see ValorLook). */
@Mixin(AvatarRenderer.class)
abstract class ValorHandMixin {
	@Inject(method = "renderHand", at = @At("TAIL"))
	private void jaymoskingdoms$valorHand(PoseStack poseStack, SubmitNodeCollector collector, int lightCoords, Identifier skinTexture, ModelPart arm,
		boolean hasSleeve, CallbackInfo ci) {
		ValorLook.firstPersonHand(poseStack, collector, lightCoords, arm, ((AvatarRenderer<?>) (Object) this).getModel());
	}
}
