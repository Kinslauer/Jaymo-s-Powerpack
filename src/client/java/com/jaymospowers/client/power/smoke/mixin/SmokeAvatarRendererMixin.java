package com.jaymospowers.client.power.smoke.mixin;

import com.jaymospowers.client.power.smoke.SmokePowerClient;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.world.entity.Avatar;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Vapour Form's smoke legs go on the player's render state (SmokeFlightPose). */
@Mixin(AvatarRenderer.class)
abstract class SmokeAvatarRendererMixin {
	@Inject(
		method = "extractRenderState(Lnet/minecraft/world/entity/Avatar;Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;F)V",
		at = @At("TAIL"))
	private void jaymospowers$smokeVapour(Avatar entity, AvatarRenderState state, float partialTicks, CallbackInfo ci) {
		SmokePowerClient.extract(entity, state);
	}
}
