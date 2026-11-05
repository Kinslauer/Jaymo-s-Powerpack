package com.jaymoskingdoms.client.mixin.vampires;

import com.jaymoskingdoms.NightGifts;
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

/**
 * (2026-11-05) No human arm in first person while you're the beast. Invisibility used to hide it, but an invisible player
 * is all but ignored by monsters (Jung "seemed to be invulnerable"), so the beast's player isn't invisible any more.
 */
@Mixin(AvatarRenderer.class)
abstract class BeastHidesHandMixin {
	@Inject(method = "renderHand", at = @At("HEAD"), cancellable = true)
	private void jaymoskingdoms$beastHidesHand(PoseStack poseStack, SubmitNodeCollector collector, int lightCoords, Identifier skinTexture, ModelPart arm,
		boolean hasSleeve, CallbackInfo ci) {
		Minecraft minecraft = Minecraft.getInstance();
		NightGifts.Gift gift = minecraft.player == null ? null : minecraft.player.getAttached(NightGifts.GIFT);
		if (gift != null && gift.wolf() && gift.beast()) {
			ci.cancel();
		}
	}
}
