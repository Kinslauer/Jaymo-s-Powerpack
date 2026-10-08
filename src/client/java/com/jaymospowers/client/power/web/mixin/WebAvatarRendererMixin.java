package com.jaymospowers.client.power.web.mixin;

import com.jaymospowers.client.power.web.WebArms;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.world.entity.Avatar;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Puts the web-slinger arm and leg pose on a player's render state (see WebArms). */
@Mixin(AvatarRenderer.class)
abstract class WebAvatarRendererMixin {
	@Inject(
		method = "extractRenderState(Lnet/minecraft/world/entity/Avatar;Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;F)V",
		at = @At("TAIL"))
	private void jaymospowers$webArms(Avatar entity, AvatarRenderState state, float partialTicks, CallbackInfo ci) {
		WebArms.extract(entity, state, partialTicks);
	}
}
