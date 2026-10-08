package com.jaymospowers.client.power.wind.mixin;

import com.jaymospowers.client.power.wind.WindPose;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.world.entity.Avatar;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Puts the wind flight pose amount on a player's render state (see WindPose). */
@Mixin(AvatarRenderer.class)
abstract class AvatarRendererMixin {
	@Inject(
		method = "extractRenderState(Lnet/minecraft/world/entity/Avatar;Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;F)V",
		at = @At("TAIL"))
	private void jaymospowers$windPose(Avatar entity, AvatarRenderState state, float partialTicks, CallbackInfo ci) {
		WindPose.extract(entity, state, partialTicks);
	}
}
