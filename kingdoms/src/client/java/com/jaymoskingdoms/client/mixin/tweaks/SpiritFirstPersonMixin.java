package com.jaymoskingdoms.client.mixin.tweaks;

import com.jaymoskingdoms.client.spirits.SpiritArms;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.FirstPersonHandsAndItemsRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.state.level.FirstPersonHandsAndItemsRenderState;
import net.minecraft.client.renderer.state.level.PlayerRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** (0.7.241) First person: after the hands, your own spirit's arms ({@link SpiritArms#firstPerson}). */
@Mixin(FirstPersonHandsAndItemsRenderer.class)
abstract class SpiritFirstPersonMixin {
	@Inject(method = "submitHandsWithItems", at = @At("TAIL"))
	private void jaymoskingdoms$spiritArms(float partialTicks, PoseStack poseStack, SubmitNodeCollector collector, PlayerRenderState playerState,
		FirstPersonHandsAndItemsRenderState state, CallbackInfo ci) {
		if (playerState.hasPlayer) {
			SpiritArms.firstPerson(poseStack, collector, playerState.avatarRenderState);
		}
	}
}
