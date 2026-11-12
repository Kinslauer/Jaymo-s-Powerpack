package com.jaymoskingdoms.client.mixin.thirst;

import com.jaymoskingdoms.client.thirst.ThirstClient;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** No human arm in first person while you are a bat, a mist or a wolf. */
@Mixin(AvatarRenderer.class)
abstract class ThirstHidesHandMixin {
	@Inject(method = "renderHand", at = @At("HEAD"), cancellable = true)
	private void jaymoskingdoms$thirstHidesHand(PoseStack poseStack, SubmitNodeCollector collector, int lightCoords, Identifier skinTexture, ModelPart arm,
		boolean hasSleeve, CallbackInfo ci) {
		if (ThirstClient.shaped(Minecraft.getInstance().player)) {
			ci.cancel();
		}
	}
}
