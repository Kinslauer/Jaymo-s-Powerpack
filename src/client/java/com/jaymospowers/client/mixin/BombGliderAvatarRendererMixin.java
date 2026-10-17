package com.jaymospowers.client.mixin;

import com.jaymospowers.armory.BombGlider;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.world.entity.Avatar;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** (0.13.7) A bomb glider's rider stands on its deck, feet still, rather than sitting. */
@Mixin(AvatarRenderer.class)
abstract class BombGliderAvatarRendererMixin {
	@Inject(
		method = "extractRenderState(Lnet/minecraft/world/entity/Avatar;Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;F)V",
		at = @At("TAIL"))
	private void jaymospowers$standOnGlider(Avatar entity, AvatarRenderState state, float partialTicks, CallbackInfo ci) {
		if (entity.getVehicle() instanceof BombGlider) {
			state.isPassenger = false;
			state.walkAnimationSpeed = 0.0F;
		}
	}
}
