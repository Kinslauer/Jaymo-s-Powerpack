package com.jaymospowers.client.power.web.mixin;

import com.jaymospowers.client.power.web.WebSymbioteClient;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.FirstPersonHandsAndItemsRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.state.level.FirstPersonHandsAndItemsRenderState;
import net.minecraft.client.renderer.state.level.PlayerRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** First person without First-person Model: Living Armor's tendrils drawn by the hands (see WebTendrilLayer.submitFirstPerson). */
@Mixin(FirstPersonHandsAndItemsRenderer.class)
abstract class WebFirstPersonHandsMixin {
	@Inject(method = "submitHandsWithItems", at = @At("TAIL"))
	private void jaymospowers$tendrils(float partialTicks, PoseStack poseStack, SubmitNodeCollector collector, PlayerRenderState player,
		FirstPersonHandsAndItemsRenderState hands, CallbackInfo ci) {
		WebSymbioteClient.firstPersonTendrils(poseStack, collector, player.avatarRenderState);
	}
}
